package org.novasos.healthysv2.billing;

import static org.novasos.healthysv2.billing.api.CashRegisterDtos.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import org.novasos.healthysv2.billing.api.CareBillingDtos.Receipt;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read and close the immutable cash ledger without modifying its operations. Periods are [from,to). */
@Service
@Transactional
class CashRegisterService {
    private final JdbcTemplate jdbc;
    private final CurrentUserContext users;
    private final LicensingAccess licenses;
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    private final int retentionYears;

    CashRegisterService(JdbcTemplate jdbc,CurrentUserContext users,LicensingAccess licenses,
            @Value("${healthys.care.retention-years:30}") int retentionYears) {
        this.jdbc=jdbc; this.users=users; this.licenses=licenses; this.retentionYears=retentionYears;
        if(retentionYears < 1) throw new IllegalArgumentException("Retention must be positive");
    }

    @Transactional(readOnly=true)
    CashReport report(Instant from,Instant to,UUID cashier) {
        validatePeriod(from,to,false,Instant.now());
        var scope=readScope(cashier);
        var receipts=operations(scope.organization(),scope.cashier(),from,to);
        return aggregate(from,to,scope.cashier(),receipts);
    }

    CashClosure close(String key,CloseCashRequest request) {
        CareBillingService.validateKey(key);
        UUID org=organization(); lockOrganization(org);
        var scope=closeScope(request.cashierPersonId());
        Instant now=Instant.now(); validatePeriod(request.from(),request.to(),true,now);
        String fingerprint=fingerprint(scope.cashier(),request);
        var replay=jdbc.queryForList("select id,fingerprint from billing.cash_closure where organization_id=? and idempotency_key=?",org,key);
        if(!replay.isEmpty()) {
            if(!fingerprint.equals(replay.getFirst().get("fingerprint")))
                throw conflict("IDEMPOTENCY_CONFLICT","Closing key was used for a different declaration");
            return closure((UUID)replay.getFirst().get("id"));
        }
        int overlap=Objects.requireNonNull(jdbc.queryForObject("""
            select count(*) from billing.cash_closure where organization_id=? and cashier_person_id=?
                and period_start < ? and period_end > ?
            """,Integer.class,org,scope.cashier(),Timestamp.from(request.to()),Timestamp.from(request.from())));
        if(overlap>0) throw conflict("CASH_PERIOD_ALREADY_CLOSED","This cashier already has a closing overlapping the requested period");
        var receipts=operations(org,scope.cashier(),request.from(),request.to());
        for(var receipt:receipts) {
            Integer linked=jdbc.queryForObject("select count(*) from billing.cash_closure_receipt where cash_operation_id=?",Integer.class,receipt.id());
            if(linked!=null && linked>0) throw conflict("CASH_RECEIPT_ALREADY_CLOSED","An operation is already included in a closing");
        }
        var amounts=reconcile(totals(receipts),request.declaredAmounts());
        UUID id=UUID.randomUUID(),actor=users.current().personId();
        if(actor==null) throw new AccessDeniedException("CLOSING_ACTOR_REQUIRED");
        String number="CLOSE-"+id.toString().replace("-","").substring(0,20).toUpperCase(Locale.ROOT);
        String note=request.note()==null?null:request.note().trim();
        jdbc.update("""
            insert into billing.cash_closure(id,closure_number,organization_id,cashier_person_id,period_start,period_end,
                created_at,created_by_person_id,note,amounts,idempotency_key,fingerprint,retention_expires_at)
            values (?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?)
            """,id,number,org,scope.cashier(),Timestamp.from(request.from()),Timestamp.from(request.to()),Timestamp.from(now),actor,note,
            encode(amounts),key,fingerprint,Timestamp.from(now.atZone(ZoneOffset.UTC).plusYears(retentionYears).toInstant()));
        for(var receipt:receipts) jdbc.update("insert into billing.cash_closure_receipt(closure_id,cash_operation_id) values (?,?)",id,receipt.id());
        jdbc.update("insert into audit.audit_log(actor_person_id,organization_id,module,entity_type,entity_id,action) values (?,?,'BILLING','CashClosure',?,'CLOSE_CASH_REGISTER')",actor,org,id);
        return new CashClosure(id,number,request.from(),request.to(),scope.cashier(),now,actor,note,amounts,receipts);
    }

    @Transactional(readOnly=true)
    List<CashClosure> closures(UUID cashier) {
        var scope=readScope(cashier);
        List<UUID> ids;
        if(scope.cashier()==null) ids=jdbc.query("select id from billing.cash_closure where organization_id=? order by created_at desc",(rs,n)->rs.getObject(1,UUID.class),scope.organization());
        else ids=jdbc.query("select id from billing.cash_closure where organization_id=? and cashier_person_id=? order by created_at desc",(rs,n)->rs.getObject(1,UUID.class),scope.organization(),scope.cashier());
        return ids.stream().map(this::closure).toList();
    }

    @Transactional(readOnly=true)
    CashClosure closure(UUID id) {
        var rows=jdbc.queryForList("select * from billing.cash_closure where id=?",id);
        if(rows.isEmpty()) throw new ResourceNotFoundException("Cash closing",id);
        var row=rows.getFirst();
        var scope=readScope((UUID)row.get("cashier_person_id"));
        if(!scope.organization().equals(row.get("organization_id"))) throw new AccessDeniedException("CASH_CLOSING_ORGANIZATION_DENIED");
        var receipts=jdbc.query("""
            select o.* from billing.cash_operation o join billing.cash_closure_receipt l on l.cash_operation_id=o.id
                where l.closure_id=? order by o.occurred_at,o.id
            """,(rs,n)->receipt(rs),id);
        return new CashClosure(id,(String)row.get("closure_number"),instant(row.get("period_start")),instant(row.get("period_end")),
            (UUID)row.get("cashier_person_id"),instant(row.get("created_at")),(UUID)row.get("created_by_person_id"),
            (String)row.get("note"),decode(row.get("amounts").toString()),receipts);
    }

    private List<Receipt> operations(UUID org,UUID cashier,Instant from,Instant to) {
        String sql="""
            select o.* from billing.cash_operation o join billing.invoice i on i.id=o.invoice_id
                where i.organization_id=? and o.occurred_at >= ? and o.occurred_at < ?
            """;
        if(cashier==null) return jdbc.query(sql+" order by o.occurred_at,o.id",(rs,n)->receipt(rs),org,Timestamp.from(from),Timestamp.from(to));
        return jdbc.query(sql+" and o.cashier_person_id=? order by o.occurred_at,o.id",(rs,n)->receipt(rs),org,Timestamp.from(from),Timestamp.from(to),cashier);
    }

    private Scope readScope(UUID requested) {
        UUID org=organization(); licenses.requireMember(org);
        var user=users.current();
        if(user.hasAny("PLATFORM_ADMIN","HOSPITAL_ADMIN")) {
            licenses.requireAdministrator(org); return new Scope(org,requested);
        }
        if(user.has("ACCOUNTANT") && hasRole(org,user.personId(),"ACCOUNTANT")) return new Scope(org,requested);
        if(!user.has("CASHIER") || user.personId()==null || !hasRole(org,user.personId(),"CASHIER"))
            throw new AccessDeniedException("CASH_REPORT_ROLE_REQUIRED");
        if(requested!=null && !requested.equals(user.personId())) throw new AccessDeniedException("CASHIER_OWN_REPORT_ONLY");
        return new Scope(org,user.personId());
    }

    private Scope closeScope(UUID requested) {
        UUID org=organization(); licenses.requireMember(org); licenses.requireActive(org);
        var user=users.current();
        UUID cashier=requested==null?user.personId():requested;
        if(user.hasAny("PLATFORM_ADMIN","HOSPITAL_ADMIN")) {
            licenses.requireAdministrator(org);
        } else {
            if(!user.has("CASHIER") || !Objects.equals(user.personId(),cashier)) throw new AccessDeniedException("CASHIER_OWN_CLOSING_ONLY");
            Integer active=jdbc.queryForObject("select count(*) from licensing.member where organization_id=? and person_id=? and role='CASHIER' and status='ACTIVE'",Integer.class,org,cashier);
            if(active==null || active==0) throw new AccessDeniedException("CASHIER_REQUIRED");
        }
        if(cashier==null) throw new BusinessRuleException("CLOSING_CASHIER_REQUIRED","Choose the cashier whose register is being closed");
        Integer historical=jdbc.queryForObject("""
            select count(*) from identity.person p where p.id=? and
                (exists(select 1 from licensing.member m where m.organization_id=? and m.person_id=p.id and m.role='CASHIER')
                 or exists(select 1 from billing.cash_operation o join billing.invoice i on i.id=o.invoice_id
                    where i.organization_id=? and o.cashier_person_id=p.id))
            """,Integer.class,cashier,org,org);
        if(historical==null || historical==0) throw new AccessDeniedException("ESTABLISHMENT_CASHIER_REQUIRED");
        return new Scope(org,cashier);
    }

    private boolean hasRole(UUID org,UUID person,String role) {
        if(person==null) return false;
        Integer n=jdbc.queryForObject("select count(*) from licensing.member where organization_id=? and person_id=? and role=? and status='ACTIVE'",Integer.class,org,person,role);
        return n!=null && n>0;
    }
    private UUID organization() {
        UUID org=users.current().organizationId();
        if(org==null) throw new AccessDeniedException("ORGANIZATION_CONTEXT_REQUIRED"); return org;
    }
    private void lockOrganization(UUID org) {
        if(jdbc.queryForList("select id from organization.organization where id=? for update",org).isEmpty())
            throw new ResourceNotFoundException("Organization",org);
    }
    private Receipt receipt(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Receipt(rs.getObject("id",UUID.class),rs.getString("receipt_number"),rs.getObject("invoice_id",UUID.class),
            rs.getString("operation_type"),rs.getBigDecimal("amount"),rs.getString("currency"),rs.getString("reason"),
            rs.getTimestamp("occurred_at").toInstant(),rs.getObject("cashier_person_id",UUID.class));
    }
    static CashReport aggregate(Instant from,Instant to,UUID cashier,List<Receipt> receipts) {
        var perCashier=new TreeMap<UUID,List<Receipt>>();
        for(var receipt:receipts) perCashier.computeIfAbsent(receipt.cashierPersonId(),unused->new ArrayList<>()).add(receipt);
        var cashierTotals=perCashier.entrySet().stream().map(e->new CashierTotals(e.getKey(),totals(e.getValue()))).toList();
        return new CashReport(from,to,cashier,totals(receipts),cashierTotals);
    }
    static List<CurrencyTotals> totals(List<Receipt> receipts) {
        var byCurrency=new TreeMap<String,Accumulator>();
        for(var receipt:receipts) {
            var accumulator=byCurrency.computeIfAbsent(receipt.currency(),unused->new Accumulator());
            if("PAYMENT".equals(receipt.operationType())) accumulator.payments=accumulator.payments.add(receipt.amount());
            else if("REFUND".equals(receipt.operationType())) accumulator.refunds=accumulator.refunds.add(receipt.amount());
            else throw new IllegalArgumentException("Unsupported cash operation");
            accumulator.count++;
        }
        return byCurrency.entrySet().stream().map(e->new CurrencyTotals(e.getKey(),Invoice.money(e.getValue().payments),
            Invoice.money(e.getValue().refunds),Invoice.money(e.getValue().payments.subtract(e.getValue().refunds)),e.getValue().count)).toList();
    }
    static List<ClosureAmount> reconcile(List<CurrencyTotals> expected,List<DeclaredAmount> declarations) {
        var declared=new TreeMap<String,BigDecimal>();
        for(var amount:declarations) {
            String currency=amount.currency().trim().toUpperCase(Locale.ROOT);
            if(declared.putIfAbsent(currency,Invoice.money(amount.amount()))!=null)
                throw new BusinessRuleException("DUPLICATE_DECLARED_CURRENCY","Declare each currency once");
        }
        var all=new TreeMap<String,CurrencyTotals>();
        for(var total:expected) all.put(total.currency(),total);
        for(var currency:declared.keySet()) all.putIfAbsent(currency,new CurrencyTotals(currency,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,0));
        var result=new ArrayList<ClosureAmount>();
        for(var total:all.values()) {
            if(!declared.containsKey(total.currency())) throw new BusinessRuleException("MISSING_DECLARED_CURRENCY","Declare cash counted for every currency in the register");
            BigDecimal counted=declared.get(total.currency());
            result.add(new ClosureAmount(total.currency(),Invoice.money(total.payments()),Invoice.money(total.refunds()),Invoice.money(total.net()),counted,Invoice.money(counted.subtract(total.net()))));
        }
        return result;
    }
    static void validatePeriod(Instant from,Instant to,boolean closing,Instant now) {
        if(from==null || to==null || !from.isBefore(to)) throw new BusinessRuleException("INVALID_CASH_PERIOD","The register period must have an end after its start");
        if(closing && to.isAfter(now)) throw new BusinessRuleException("FUTURE_CASH_CLOSING","A future register period cannot be closed");
    }
    static boolean within(Instant value,Instant from,Instant to) { return !value.isBefore(from) && value.isBefore(to); }
    static String fingerprint(UUID cashier,CloseCashRequest request) {
        String amounts=request.declaredAmounts().stream().sorted(Comparator.comparing(a->a.currency().toUpperCase(Locale.ROOT)))
            .map(a->a.currency().trim().toUpperCase(Locale.ROOT)+":"+Invoice.money(a.amount()).toPlainString()).reduce("",(a,b)->a+";"+b);
        return cashier+"|"+request.from()+"|"+request.to()+"|"+amounts+"|"+(request.note()==null?"":request.note().trim());
    }
    private String encode(List<ClosureAmount> amounts) {
        try { return json.writeValueAsString(amounts); } catch(JsonProcessingException e) { throw new IllegalStateException("Cannot encode closing",e); }
    }
    private List<ClosureAmount> decode(String amounts) {
        try { return json.readValue(amounts,new TypeReference<>(){}); } catch(JsonProcessingException e) { throw new IllegalStateException("Cannot read closing",e); }
    }
    private static Instant instant(Object value) { return ((Timestamp)value).toInstant(); }
    private ConflictException conflict(String code,String message) { return new ConflictException(code,message); }
    private record Scope(UUID organization,UUID cashier) {}
    private static class Accumulator {
        BigDecimal payments=BigDecimal.ZERO,refunds=BigDecimal.ZERO;
        long count;
    }
}
