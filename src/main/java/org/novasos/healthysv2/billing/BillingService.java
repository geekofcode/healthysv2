package org.novasos.healthysv2.billing;

import static org.novasos.healthysv2.billing.api.BillingDtos.*;

import java.util.*;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class BillingService {
    private static final String[] BILLING_ROLES = {"HOSPITAL_ADMIN","HOSPITAL_AGENT","CASHIER","ACCOUNTANT"};
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final JdbcTemplate jdbc;
    private final CurrentUserContext users;

    BillingService(InvoiceRepository invoices, PaymentRepository payments, JdbcTemplate jdbc, CurrentUserContext users) {
        this.invoices=invoices; this.payments=payments; this.jdbc=jdbc; this.users=users;
    }

    InvoiceResponse create(CreateInvoiceRequest request) {
        authorizeWrite(request.organizationId()); validateContext(request);
        Invoice invoice;
        try {
            invoice=Invoice.create(request.patientId(),request.organizationId(),request.encounterId(),request.currency(),request.dueAt());
            request.items().forEach(item->invoice.addItem(item.itemType(),item.referenceId(),item.description(),
                    item.quantity(),item.unitPrice(),item.taxAmount()));
        } catch (IllegalArgumentException exception) {
            throw rule("INVALID_INVOICE", "error.billing.invoice-values");
        }
        invoices.saveAndFlush(invoice); audit(invoice,"CREATE_INVOICE"); return response(invoice);
    }

    @Transactional(readOnly=true)
    InvoiceResponse find(UUID id) { var invoice=get(id); authorizeRead(invoice); return response(invoice); }

    @Transactional(readOnly=true)
    PageResponse<InvoiceSummary> search(UUID patient,UUID organization,String status,Pageable pageable) {
        var scope=readScope(patient,organization);
        return PageResponse.from(invoices.search(scope.patient(),scope.organization(),normalize(status),pageable).map(this::summary));
    }

    InvoiceResponse issue(UUID id) {
        var invoice=forWrite(id);
        try { invoice.issue(); invoices.flush(); audit(invoice,"ISSUE_INVOICE"); return response(invoice); }
        catch(IllegalStateException exception){throw rule("INVOICE_CANNOT_ISSUE","error.billing.cannot-issue");}
    }

    InvoiceResponse cancel(UUID id) {
        var invoice=forWrite(id);
        try { invoice.cancel(); invoices.flush(); audit(invoice,"CANCEL_INVOICE"); return response(invoice); }
        catch(IllegalStateException exception){throw rule("INVOICE_CANNOT_CANCEL","error.billing.cannot-cancel");}
    }

    PaymentResponse recordPayment(UUID invoiceId,RecordPaymentRequest request) {
        var invoice=forWrite(invoiceId);
        try {
            invoice.recordPayment(request.amount(),request.currency());
            var payment=payments.save(Payment.complete(invoice.getId(),request.amount(),invoice.getCurrency(),
                    request.paymentMethod(),request.provider(),request.externalTransactionId()));
            invoices.flush(); payments.flush(); audit(invoice,"RECORD_PAYMENT"); audit(payment,invoice.getOrganizationId());
            return payment(payment);
        } catch(IllegalArgumentException exception){throw rule("INVALID_PAYMENT","error.billing.payment-values");}
          catch(IllegalStateException exception){throw rule("INVOICE_NOT_PAYABLE","error.billing.not-payable");}
    }

    @Transactional(readOnly=true)
    List<PaymentResponse> listPayments(UUID invoiceId) {
        var invoice=get(invoiceId); authorizeRead(invoice);
        return payments.findByInvoiceIdOrderByPaidAtDesc(invoiceId).stream().map(this::payment).toList();
    }

    private void validateContext(CreateInvoiceRequest request) {
        require("patient.patient",request.patientId(),"Patient"); require("organization.organization",request.organizationId(),"Organization");
        if(request.encounterId()!=null&&!exists("select count(*) from registration.encounter where id=? and patient_id=? and organization_id=?",
                request.encounterId(),request.patientId(),request.organizationId()))
            throw rule("INVOICE_ENCOUNTER_MISMATCH","error.billing.encounter");
    }

    private Invoice forWrite(UUID id){var invoice=invoices.lock(id).orElseThrow(()->notFound("Invoice",id));authorizeWrite(invoice.getOrganizationId());return invoice;}
    private Invoice get(UUID id){return invoices.findById(id).orElseThrow(()->notFound("Invoice",id));}
    private void authorizeWrite(UUID organization) {
        var user=users.current(); if(user.has("PLATFORM_ADMIN"))return;
        if(!user.hasAny(BILLING_ROLES)||user.organizationId()==null||!user.organizationId().equals(organization))throw new AccessDeniedException("BILLING_WRITE_DENIED");
    }
    private void authorizeRead(Invoice invoice) {
        var user=users.current(); if(user.has("PLATFORM_ADMIN"))return;
        if(user.hasAny(BILLING_ROLES)&&Objects.equals(user.organizationId(),invoice.getOrganizationId()))return;
        if(user.has("PATIENT")&&user.personId()!=null&&exists("select count(*) from patient.patient where id=? and person_id=?",invoice.getPatientId(),user.personId()))return;
        throw new AccessDeniedException("BILLING_READ_DENIED");
    }
    private ReadScope readScope(UUID patient,UUID organization) {
        var user=users.current(); if(user.has("PLATFORM_ADMIN"))return new ReadScope(patient,organization);
        if(user.hasAny(BILLING_ROLES)){
            if(user.organizationId()==null||(organization!=null&&!organization.equals(user.organizationId())))throw new AccessDeniedException("BILLING_ORGANIZATION_DENIED");
            return new ReadScope(patient,user.organizationId());
        }
        if(user.has("PATIENT")&&user.personId()!=null){
            UUID own=jdbc.query("select id from patient.patient where person_id=? limit 1",rs->rs.next()?(UUID)rs.getObject(1):null,user.personId());
            if(own==null||(patient!=null&&!patient.equals(own)))throw new AccessDeniedException("BILLING_PATIENT_DENIED");
            return new ReadScope(own,organization);
        }
        throw new AccessDeniedException("BILLING_READ_DENIED");
    }

    private InvoiceSummary summary(Invoice i){return new InvoiceSummary(i.getId(),i.getNumber(),i.getPatientId(),i.getOrganizationId(),i.getCurrency(),i.getTotalAmount(),i.getAmountPaid(),i.balance(),i.getIssuedAt(),i.getDueAt(),i.getStatus());}
    private InvoiceResponse response(Invoice i){return new InvoiceResponse(i.getId(),i.getNumber(),i.getPatientId(),i.getOrganizationId(),i.getEncounterId(),i.getCurrency(),i.getSubtotal(),i.getTaxAmount(),i.getTotalAmount(),i.getAmountPaid(),i.balance(),i.getIssuedAt(),i.getDueAt(),i.getStatus(),i.getItems().stream().map(this::item).toList(),payments.findByInvoiceIdOrderByPaidAtDesc(i.getId()).stream().map(this::payment).toList());}
    private InvoiceItemResponse item(InvoiceItem i){return new InvoiceItemResponse(i.getId(),i.getType(),i.getReferenceId(),i.getDescription(),i.getQuantity(),i.getUnitPrice(),i.getTaxAmount(),i.getTotalAmount());}
    private PaymentResponse payment(Payment p){return new PaymentResponse(p.getId(),p.getNumber(),p.getInvoiceId(),p.getAmount(),p.getCurrency(),p.getMethod(),p.getPaidAt(),p.getStatus(),p.getTransactions().stream().map(t->new PaymentTransactionResponse(t.getId(),t.getProvider(),t.getExternalReference(),t.getType(),t.getAmount(),t.getStatus(),t.getOccurredAt())).toList());}
    private void audit(Invoice i,String action){jdbc.update("insert into audit.audit_log(actor_person_id,organization_id,module,entity_type,entity_id,action,new_value) values (?,?,?,?,?,?,?::jsonb)",users.current().personId(),i.getOrganizationId(),"BILLING","Invoice",i.getId(),action,"{\"status\":\""+i.getStatus()+"\",\"balance\":"+i.balance()+"}");}
    private void audit(Payment p,UUID organization){jdbc.update("insert into audit.audit_log(actor_person_id,organization_id,module,entity_type,entity_id,action,new_value) values (?,?,?,?,?,'RECORD_PAYMENT',?::jsonb)",users.current().personId(),organization,"BILLING","Payment",p.getId(),"{\"invoiceId\":\""+p.getInvoiceId()+"\",\"amount\":"+p.getAmount()+"}");}
    private void require(String table,UUID id,String type){if(!exists("select count(*) from "+table+" where id=?",id))throw notFound(type,id);}
    private boolean exists(String sql,Object...args){return Objects.requireNonNull(jdbc.queryForObject(sql,Integer.class,args))>0;}
    private String normalize(String value){return value==null||value.isBlank()?null:value.trim().toUpperCase(Locale.ROOT);}
    private ResourceNotFoundException notFound(String type,Object id){return new ResourceNotFoundException(type,id);}
    private BusinessRuleException rule(String code,String key){return new BusinessRuleException(code,key);}
    private record ReadScope(UUID patient,UUID organization){}
}
