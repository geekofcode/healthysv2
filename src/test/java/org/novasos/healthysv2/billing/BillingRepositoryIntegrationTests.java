package org.novasos.healthysv2.billing;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test") @SpringBootTest @Import(TestcontainersConfiguration.class) @Transactional
class BillingRepositoryIntegrationTests {
    @Autowired InvoiceRepository invoices; @Autowired PaymentRepository payments; @Autowired JdbcTemplate jdbc;

    @Test void persistsInvoiceItemsPaymentsAndTransactions() {
        var graph=graph(); var invoice=Invoice.create(graph.patient,graph.organization,null,"CAD",null);
        invoice.addItem("CONSULTATION",null,"Consultation",BigDecimal.ONE,new BigDecimal("80"),new BigDecimal("12"));
        invoice.issue(); invoice.recordPayment(new BigDecimal("92"),"CAD"); invoices.saveAndFlush(invoice);
        payments.saveAndFlush(Payment.complete(invoice.getId(),new BigDecimal("92"),"CAD","CARD","STRIPE","txn-"+UUID.randomUUID()));
        var found=invoices.findById(invoice.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo("PAID");
        assertThat(found.getItems()).singleElement().extracting(InvoiceItem::getTotalAmount).isEqualTo(new BigDecimal("92.00"));
        assertThat(payments.findByInvoiceIdOrderByPaidAtDesc(invoice.getId())).singleElement().satisfies(payment->{
            assertThat(payment.getMethod()).isEqualTo("CARD"); assertThat(payment.getTransactions()).hasSize(1);
        });
    }
    private Graph graph(){UUID person=UUID.randomUUID(),patient=UUID.randomUUID(),organization=UUID.randomUUID();jdbc.update("insert into identity.person(id,person_number,first_name,last_name,status) values (?,?,?,?,?)",person,"PER-"+person,"Bill","Patient","ACTIVE");jdbc.update("insert into patient.patient(id,person_id,patient_number) values (?,?,?)",patient,person,"PAT-"+patient);jdbc.update("insert into organization.organization(id,organization_number,name) values (?,?,?)",organization,"ORG-"+organization,"Billing Clinic");return new Graph(patient,organization);}
    private record Graph(UUID patient,UUID organization){}
}
