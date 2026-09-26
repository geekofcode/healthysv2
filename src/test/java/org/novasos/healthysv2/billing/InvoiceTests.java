package org.novasos.healthysv2.billing;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InvoiceTests {
    @Test void calculatesTotalsAndCompletesPaymentLifecycle() {
        var invoice=Invoice.create(UUID.randomUUID(),UUID.randomUUID(),null,"cad",Instant.now().plusSeconds(3600));
        invoice.addItem("CONSULTATION",UUID.randomUUID(),"Consultation",new BigDecimal("2"),new BigDecimal("50"),new BigDecimal("15"));
        assertThat(invoice.getSubtotal()).isEqualByComparingTo("100.00");
        assertThat(invoice.getTotalAmount()).isEqualByComparingTo("115.00");
        invoice.issue(); invoice.recordPayment(new BigDecimal("40"),"CAD");
        assertThat(invoice.getStatus()).isEqualTo("PARTIALLY_PAID");
        assertThat(invoice.balance()).isEqualByComparingTo("75.00");
        invoice.recordPayment(new BigDecimal("75"),"CAD");
        assertThat(invoice.getStatus()).isEqualTo("PAID");
    }

    @Test void rejectsInvalidStateTransitionsAndOverpayment() {
        var invoice=Invoice.create(UUID.randomUUID(),UUID.randomUUID(),null,"USD",null);
        invoice.addItem("SERVICE",null,"Service",BigDecimal.ONE,new BigDecimal("25"),BigDecimal.ZERO);
        assertThatThrownBy(()->invoice.recordPayment(BigDecimal.ONE,"USD")).isInstanceOf(IllegalStateException.class);
        invoice.issue();
        assertThatThrownBy(()->invoice.recordPayment(new BigDecimal("26"),"USD")).isInstanceOf(IllegalArgumentException.class);
        invoice.recordPayment(new BigDecimal("10"),"USD");
        assertThatThrownBy(invoice::cancel).isInstanceOf(IllegalStateException.class);
    }

    @Test void refusesEmptyInvoicesAndUnsupportedPaymentMethods() {
        var invoice=Invoice.create(UUID.randomUUID(),UUID.randomUUID(),null,"EUR",null);
        assertThatThrownBy(invoice::issue).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->Payment.complete(invoice.getId(),BigDecimal.ONE,"EUR","CRYPTO",null,null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
