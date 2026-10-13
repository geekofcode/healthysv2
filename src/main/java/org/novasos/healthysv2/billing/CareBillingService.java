package org.novasos.healthysv2.billing;

import static org.novasos.healthysv2.billing.api.CareBillingDtos.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.novasos.healthysv2.billing.api.BillingDtos.InvoiceResponse;
import org.novasos.healthysv2.care.api.CareSettlement;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Catalogue prices are frozen into issued invoices; all cash mutations serialize per establishment. */
@Service
@Transactional
class CareBillingService {
    private final JdbcTemplate jdbc;
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final BillingService billing;
    private final CurrentUserContext users;
    private final LicensingAccess licenses;
    private final CareSettlement settlement;
    private final int retentionYears;

    CareBillingService(JdbcTemplate jdbc, InvoiceRepository invoices, PaymentRepository payments,
            BillingService billing, CurrentUserContext users, LicensingAccess licenses,
            CareSettlement settlement, @Value("${healthys.care.retention-years:30}") int retentionYears) {
        this.jdbc=jdbc; this.invoices=invoices; this.payments=payments; this.billing=billing;
        this.users=users; this.licenses=licenses; this.settlement=settlement;
        if(retentionYears < 1) throw new IllegalArgumentException("Retention must be positive");
        this.retentionYears=retentionYears;
    }

    @Transactional(readOnly=true)
    List<TariffResponse> tariffs(UUID org) {
        member(org);
        return jdbc.query("select * from billing.care_tariff where organization_id=? order by code",
            (rs,n)->tariff(rs), org);
    }

    TariffResponse createTariff(TariffRequest request) {
        lockOrganization(request.organizationId()); administrator(request.organizationId());
        String code=normalizeCode(request.code());
        if(count("select count(*) from billing.care_tariff where organization_id=? and code=?",request.organizationId(),code)>0)
            throw conflict("TARIFF_CODE_EXISTS", "Tariff code already exists in this establishment");
        UUID id=UUID.randomUUID();
        jdbc.update("""
            insert into billing.care_tariff(id,organization_id,code,description,currency,unit_price,tax_rate,active)
            values (?,?,?,?,?,?,?,?)
            """,id,request.organizationId(),code,request.description().trim(),currency(request.currency()),
            request.unitPrice(),request.taxRate(),request.active());
        audit(id,request.organizationId(),"CREATE_TARIFF");
        return getTariff(id);
    }

    TariffResponse updateTariff(UUID id,TariffRequest request) {
        lockOrganization(request.organizationId()); administrator(request.organizationId());
        var existing=getTariff(id);
        if(!existing.organizationId().equals(request.organizationId())) throw new AccessDeniedException("TARIFF_ORGANIZATION_IMMUTABLE");
        if(request.version()==null || request.version()!=existing.version())
            throw conflict("TARIFF_VERSION_CONFLICT","Tariff changed; reload before editing");
        String code=normalizeCode(request.code());
        if(count("select count(*) from billing.care_tariff where organization_id=? and code=? and id<>?",request.organizationId(),code,id)>0)
            throw conflict("TARIFF_CODE_EXISTS","Tariff code already exists in this establishment");
        jdbc.update("""
            update billing.care_tariff set code=?,description=?,currency=?,unit_price=?,tax_rate=?,active=?,
                version=version+1,updated_at=now() where id=?
            """,code,request.description().trim(),currency(request.currency()),request.unitPrice(),request.taxRate(),request.active(),id);
        audit(id,request.organizationId(),"UPDATE_TARIFF"); return getTariff(id);
    }

    InvoiceResponse createInvoice(UUID episodeId,String key,CreateCareInvoiceRequest request) {
        validateKey(key);
        UUID org=episodeOrganization(episodeId); lockOrganization(org); reception(org);
        var episode=lockEpisode(episodeId,org);
        String fingerprint=invoiceFingerprint(request);
        var previous=jdbc.queryForList("select invoice_id,idempotency_key,fingerprint from billing.care_invoice where episode_id=?",episodeId);
        if(!previous.isEmpty()) {
            var row=previous.getFirst();
            if(!key.equals(row.get("idempotency_key")) || !fingerprint.equals(row.get("fingerprint")))
                throw conflict("EPISODE_ALREADY_INVOICED","Episode already has an invoice; reload it");
            return billing.find((UUID)row.get("invoice_id"));
        }
        if(count("select count(*) from billing.care_invoice where organization_id=? and idempotency_key=?",org,key)>0)
            throw conflict("IDEMPOTENCY_CONFLICT","Idempotency key is already used for another episode");
        if(!"AWAITING_PAYMENT".equals(episode.get("status")) || episode.get("started_at")!=null)
            throw conflict("EPISODE_NOT_BILLABLE","Only a new unpaid episode can be invoiced");
        var lines=loadLines(request,org);
        var invoice=Invoice.create((UUID)episode.get("patient_id"),org,null,lines.getFirst().tariff().currency(),null);
        for(var line:lines) {
            var tariff=line.tariff();
            var tax=tax(line.quantity(),tariff.unitPrice(),tariff.taxRate());
            invoice.addItem("CARE_TARIFF",tariff.id(),tariff.description(),line.quantity(),tariff.unitPrice(),tax);
        }
        invoice.issue(); invoices.saveAndFlush(invoice);
        jdbc.update("insert into billing.care_invoice(episode_id,invoice_id,organization_id,idempotency_key,fingerprint) values (?,?,?,?,?)",
            episodeId,invoice.getId(),org,key,fingerprint);
        audit(invoice.getId(),org,"ISSUE_CARE_INVOICE"); return billing.find(invoice.getId());
    }

    @Transactional(readOnly=true)
    InvoiceResponse episodeInvoice(UUID episodeId) {
        UUID org=episodeOrganization(episodeId); member(org);
        UUID invoice=jdbc.query("select invoice_id from billing.care_invoice where episode_id=?",
            rs->rs.next()?(UUID)rs.getObject(1):null,episodeId);
        if(invoice==null) throw new ResourceNotFoundException("Care invoice",episodeId);
        return billing.find(invoice);
    }

    Receipt cash(UUID invoiceId,String key,CashRequest request) {
        validateKey(key);
        var context=lockCashContext(invoiceId);
        String fingerprint=operationFingerprint("PAYMENT",request.amount(),request.currency(),null);
        var repeated=replay(invoiceId,key,fingerprint); if(repeated!=null) return repeated;
        var invoice=context.invoice();
        try { invoice.recordPayment(request.amount(),request.currency()); }
        catch(IllegalArgumentException e) { throw conflict("INVALID_CASH_PAYMENT","Amount must be positive, match currency and not exceed the balance"); }
        catch(IllegalStateException e) { throw conflict("INVOICE_NOT_PAYABLE","Invoice is not payable"); }
        var payment=payments.saveAndFlush(Payment.complete(invoiceId,request.amount(),invoice.getCurrency(),"CASH",null,null));
        invoices.flush();
        var receipt=recordOperation(invoice,"PAYMENT",key,fingerprint,request.amount(),null,payment.getId());
        if(invoice.balance().signum()==0) settlement.markSettled(context.episodeId(),invoice.getOrganizationId());
        audit(receipt.id(),invoice.getOrganizationId(),"CASH_PAYMENT"); return receipt;
    }

    Receipt refund(UUID invoiceId,String key,RefundRequest request) {
        validateKey(key);
        var context=lockCashContext(invoiceId);
        String reason=request.reason().trim();
        String fingerprint=operationFingerprint("REFUND",request.amount(),request.currency(),reason);
        var repeated=replay(invoiceId,key,fingerprint); if(repeated!=null) return repeated;
        if(context.started()) throw conflict("REFUND_AFTER_FIRST_VISIT","Cash refund is unavailable after the first medical encounter");
        var invoice=context.invoice();
        try { invoice.refund(request.amount(),request.currency()); }
        catch(IllegalArgumentException e) { throw conflict("INVALID_CASH_REFUND","Refund must match currency and not exceed net cash received"); }
        invoices.flush();
        var receipt=recordOperation(invoice,"REFUND",key,fingerprint,request.amount(),reason,null);
        settlement.reopenPayment(context.episodeId(),invoice.getOrganizationId());
        audit(receipt.id(),invoice.getOrganizationId(),"CASH_REFUND"); return receipt;
    }

    @Transactional(readOnly=true)
    List<Receipt> receipts(UUID invoiceId) {
        billing.find(invoiceId); // Applies staff organization or patient ownership scope.
        return jdbc.query("select * from billing.cash_operation where invoice_id=? order by occurred_at,id",
            (rs,n)->receipt(rs),invoiceId);
    }

    private CashContext lockCashContext(UUID invoiceId) {
        var link=jdbc.queryForList("select episode_id,organization_id from billing.care_invoice where invoice_id=?",invoiceId);
        if(link.isEmpty()) throw conflict("LEGACY_INVOICE_NOT_PAYABLE","Cash collection requires an episode invoice");
        UUID org=(UUID)link.getFirst().get("organization_id"); lockOrganization(org); cashier(org);
        UUID episode=(UUID)link.getFirst().get("episode_id");
        var episodeRow=lockEpisode(episode,org);
        var invoice=invoices.lock(invoiceId).orElseThrow(()->new ResourceNotFoundException("Invoice",invoiceId));
        if(!org.equals(invoice.getOrganizationId())) throw new AccessDeniedException("INVOICE_ORGANIZATION_MISMATCH");
        return new CashContext(invoice,episode,episodeRow.get("started_at")!=null);
    }

    private List<PricedLine> loadLines(CreateCareInvoiceRequest request,UUID org) {
        if(request.items().isEmpty()) throw conflict("INVOICE_EMPTY","Select at least one tariff");
        var lines=new ArrayList<PricedLine>(); var selected=new HashSet<UUID>(); String currency=null;
        for(var item:request.items()) {
            if(!selected.add(item.tariffId())) throw conflict("DUPLICATE_TARIFF","Combine quantities for a tariff into a single line");
            var tariff=getTariff(item.tariffId());
            if(!tariff.organizationId().equals(org) || !tariff.active()) throw conflict("TARIFF_UNAVAILABLE","Tariff is not active in this establishment");
            if(currency!=null && !currency.equals(tariff.currency())) throw conflict("INVOICE_CURRENCY_MISMATCH","All tariffs must use the same currency");
            currency=tariff.currency();
            lines.add(new PricedLine(tariff,item.quantity()));
        }
        return lines;
    }

    private Receipt recordOperation(Invoice invoice,String type,String key,String fingerprint,BigDecimal amount,String reason,UUID paymentId) {
        UUID id=UUID.randomUUID(); Instant now=Instant.now();
        var receipt=new Receipt(id,"REC-"+id.toString().replace("-","").substring(0,20).toUpperCase(Locale.ROOT),
            invoice.getId(),type,Invoice.money(amount),invoice.getCurrency(),reason,now,users.current().personId());
        jdbc.update("""
            insert into billing.cash_operation(id,receipt_number,invoice_id,payment_id,operation_type,amount,currency,
                reason,cashier_person_id,idempotency_key,fingerprint,occurred_at,retention_expires_at)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,id,receipt.receiptNumber(),invoice.getId(),paymentId,type,receipt.amount(),invoice.getCurrency(),reason,
            receipt.cashierPersonId(),key,fingerprint,java.sql.Timestamp.from(now),
            java.sql.Timestamp.from(now.atZone(ZoneOffset.UTC).plusYears(retentionYears).toInstant()));
        return receipt;
    }

    private Receipt replay(UUID invoice,String key,String fingerprint) {
        var rows=jdbc.queryForList("select fingerprint from billing.cash_operation where invoice_id=? and idempotency_key=?",invoice,key);
        if(rows.isEmpty()) return null;
        if(!fingerprint.equals(rows.getFirst().get("fingerprint"))) throw conflict("IDEMPOTENCY_CONFLICT","Idempotency key was used with different payment data");
        return jdbc.queryForObject("select * from billing.cash_operation where invoice_id=? and idempotency_key=?",(rs,n)->receipt(rs),invoice,key);
    }

    private UUID episodeOrganization(UUID id) {
        UUID org=jdbc.query("select organization_id from care.care_episode where id=?",rs->rs.next()?(UUID)rs.getObject(1):null,id);
        if(org==null) throw new ResourceNotFoundException("Care episode",id); return org;
    }
    private Map<String,Object> lockEpisode(UUID id,UUID org) {
        var rows=jdbc.queryForList("select patient_id,status,started_at from care.care_episode where id=? and organization_id=? for update",id,org);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Care episode",id); return rows.getFirst();
    }
    private void lockOrganization(UUID id) {
        var rows=jdbc.queryForList("select id from organization.organization where id=? for update",id);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Organization",id);
    }
    private TariffResponse getTariff(UUID id) {
        var rows=jdbc.query("select * from billing.care_tariff where id=?",(rs,n)->tariff(rs),id);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Tariff",id); return rows.getFirst();
    }
    private TariffResponse tariff(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new TariffResponse(rs.getObject("id",UUID.class),rs.getObject("organization_id",UUID.class),
            rs.getString("code"),rs.getString("description"),rs.getString("currency"),rs.getBigDecimal("unit_price"),
            rs.getBigDecimal("tax_rate"),rs.getBoolean("active"),rs.getLong("version"));
    }
    private Receipt receipt(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Receipt(rs.getObject("id",UUID.class),rs.getString("receipt_number"),rs.getObject("invoice_id",UUID.class),
            rs.getString("operation_type"),rs.getBigDecimal("amount"),rs.getString("currency"),rs.getString("reason"),
            rs.getTimestamp("occurred_at").toInstant(),rs.getObject("cashier_person_id",UUID.class));
    }
    private void member(UUID org) {
        var user=users.current();
        if(!user.has("PLATFORM_ADMIN") && !Objects.equals(org,user.organizationId())) throw new AccessDeniedException("BILLING_ORGANIZATION_DENIED");
        licenses.requireMember(org);
    }
    private void administrator(UUID org) { member(org); licenses.requireActive(org); licenses.requireAdministrator(org); }
    private void reception(UUID org) {
        member(org); licenses.requireActive(org);
        if(!users.current().hasAny("PLATFORM_ADMIN","HOSPITAL_ADMIN","HOSPITAL_AGENT","NURSE")) throw new AccessDeniedException("RECEPTION_REQUIRED");
    }
    private void cashier(UUID org) {
        member(org); licenses.requireActive(org);
        if(!users.current().has("CASHIER") || users.current().personId()==null
                || count("select count(*) from licensing.member where organization_id=? and person_id=? and role='CASHIER' and status='ACTIVE'",org,users.current().personId())==0)
            throw new AccessDeniedException("CASHIER_REQUIRED");
    }
    private int count(String sql,Object...args) { return Objects.requireNonNull(jdbc.queryForObject(sql,Integer.class,args)); }
    private void audit(UUID entity,UUID org,String action) {
        jdbc.update("insert into audit.audit_log(actor_person_id,organization_id,module,entity_type,entity_id,action) values (?,?,'BILLING','CareBilling',?,?)",
            users.current().personId(),org,entity,action);
    }
    static BigDecimal tax(BigDecimal quantity,BigDecimal unitPrice,BigDecimal rate) {
        return Invoice.money(Invoice.money(quantity.multiply(unitPrice)).multiply(rate).divide(new BigDecimal("100")));
    }
    static String invoiceFingerprint(CreateCareInvoiceRequest request) {
        return request.items().stream().sorted(Comparator.comparing(CareInvoiceLine::tariffId))
            .map(line->line.tariffId()+":"+line.quantity().stripTrailingZeros().toPlainString()).reduce("",(a,b)->a+";"+b);
    }
    static String operationFingerprint(String type,BigDecimal amount,String currency,String reason) {
        return type+"|"+Invoice.money(amount).toPlainString()+"|"+currency(currency)+"|"+(reason==null?"":reason.trim());
    }
    static void validateKey(String key) {
        if(key==null || !key.matches("[A-Za-z0-9._:-]{8,128}")) throw new BusinessRuleException("INVALID_IDEMPOTENCY_KEY","Use an 8–128 character idempotency key");
    }
    private static String normalizeCode(String code) { return code.trim().toUpperCase(Locale.ROOT); }
    private static String currency(String value) { return value.trim().toUpperCase(Locale.ROOT); }
    private ConflictException conflict(String code,String message) { return new ConflictException(code,message); }
    private record PricedLine(TariffResponse tariff,BigDecimal quantity) {}
    private record CashContext(Invoice invoice,UUID episodeId,boolean started) {}
}
