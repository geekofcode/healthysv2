package org.novasos.healthysv2.billing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.novasos.healthysv2.billing.api.BillingDtos.RecordPaymentRequest;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;
import org.springframework.jdbc.core.JdbcTemplate;

class CashPaymentTests {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"CARD", "MOBILE_MONEY", "INSURANCE", "MUTUAL", "THIRD_PARTY", "CRYPTO"})
    void rejectsNonCashWithoutChangingBalanceOrPersistingPayment(String method) {
        var invoices = mock(InvoiceRepository.class);
        var payments = mock(PaymentRepository.class);
        var jdbc = mock(JdbcTemplate.class);
        var users = mock(CurrentUserContext.class);
        var invoice = Invoice.create(UUID.randomUUID(), UUID.randomUUID(), null, "CAD", null);
        invoice.addItem("CONSULTATION", null, "Consultation", BigDecimal.ONE, new BigDecimal("50"), BigDecimal.ZERO);
        invoice.issue();
        when(invoices.lock(invoice.getId())).thenReturn(Optional.of(invoice));
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(UUID.randomUUID(), null, Set.of("PLATFORM_ADMIN")));
        var service = new BillingService(invoices, payments, jdbc, users);

        assertThatThrownBy(() -> service.recordPayment(invoice.getId(),
                new RecordPaymentRequest(new BigDecimal("50"), "CAD", method, null, null)))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(invoice.getAmountPaid()).isEqualByComparingTo("0");
        assertThat(invoice.getStatus()).isEqualTo("ISSUED");
        verify(invoices, never()).flush();
        verifyNoInteractions(payments, jdbc);
    }

    @Test void normalizesCashAndCreatesItsTransaction() {
        var payment = Payment.complete(UUID.randomUUID(), new BigDecimal("50"), "CAD", " cash ", null, null);
        assertThat(payment.getMethod()).isEqualTo("CASH");
        assertThat(payment.getStatus()).isEqualTo("COMPLETED");
        assertThat(payment.getTransactions()).singleElement().satisfies(transaction -> {
            assertThat(transaction.getProvider()).isNull();
            assertThat(transaction.getExternalReference()).isNull();
        });
    }
}
