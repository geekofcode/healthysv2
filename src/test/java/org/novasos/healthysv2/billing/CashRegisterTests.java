package org.novasos.healthysv2.billing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.novasos.healthysv2.billing.api.CashRegisterDtos.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.billing.api.CareBillingDtos.Receipt;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;
import org.novasos.healthysv2.shared.api.error.ConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

class CashRegisterTests {
    private final UUID cashier=UUID.randomUUID(),other=UUID.randomUUID(),org=UUID.randomUUID();
    private final Instant from=Instant.parse("2026-10-01T00:00:00Z"),to=Instant.parse("2026-10-02T00:00:00Z");

    @Test void moneyIsGroupedByCurrencyAndCashierWithoutCurrencyConversion() {
        var report=CashRegisterService.aggregate(from,to,null,List.of(
            receipt("PAYMENT","CAD","100",cashier),receipt("REFUND","CAD","15",cashier),
            receipt("PAYMENT","USD","30",cashier),receipt("PAYMENT","CAD","20",other)));
        assertThat(report.totals()).hasSize(2);
        assertThat(report.totals().getFirst().currency()).isEqualTo("CAD");
        assertThat(report.totals().getFirst().payments()).isEqualByComparingTo("120");
        assertThat(report.totals().getFirst().refunds()).isEqualByComparingTo("15");
        assertThat(report.totals().getFirst().net()).isEqualByComparingTo("105");
        assertThat(report.cashiers()).hasSize(2);
        assertThat(report.cashiers().stream().filter(c->c.cashierPersonId().equals(cashier)).findFirst().orElseThrow().totals()).hasSize(2);
    }

    @Test void refundOnlyPeriodMayHaveNegativeExpectedNet() {
        var expected=CashRegisterService.totals(List.of(receipt("REFUND","CAD","10",cashier)));
        var closing=CashRegisterService.reconcile(expected,List.of(new DeclaredAmount("CAD",BigDecimal.ZERO)));
        assertThat(closing.getFirst().expectedNet()).isEqualByComparingTo("-10");
        assertThat(closing.getFirst().difference()).isEqualByComparingTo("10");
    }

    @Test void closingRecordsCountedCashDifferenceForEveryCurrency() {
        var expected=CashRegisterService.totals(List.of(receipt("PAYMENT","CAD","100",cashier)));
        var closing=CashRegisterService.reconcile(expected,List.of(new DeclaredAmount("cad",new BigDecimal("98.50")),new DeclaredAmount("USD",new BigDecimal("5"))));
        assertThat(closing).hasSize(2);
        assertThat(closing.getFirst().difference()).isEqualByComparingTo("-1.50");
        assertThat(closing.getLast().expectedNet()).isEqualByComparingTo("0");
        assertThat(closing.getLast().difference()).isEqualByComparingTo("5");
    }

    @Test void everyObservedCurrencyMustBeDeclaredExactlyOnce() {
        var expected=CashRegisterService.totals(List.of(receipt("PAYMENT","CAD","100",cashier)));
        assertThatThrownBy(()->CashRegisterService.reconcile(expected,List.of())).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(()->CashRegisterService.reconcile(expected,List.of(new DeclaredAmount("CAD",BigDecimal.ONE),new DeclaredAmount("cad",BigDecimal.TEN))))
            .isInstanceOf(BusinessRuleException.class);
    }

    @Test void adjacentRegisterPeriodsDoNotDoubleCountBoundaryReceipts() {
        assertThat(CashRegisterService.within(from,from,to)).isTrue();
        assertThat(CashRegisterService.within(to,from,to)).isFalse();
        assertThat(CashRegisterService.within(to,to,to.plusSeconds(1))).isTrue();
        assertThat(CashRegisterService.within(from.minusNanos(1),from,to)).isFalse();
    }

    @Test void cannotCloseFutureOrEmptyPeriods() {
        assertThatThrownBy(()->CashRegisterService.validatePeriod(from,from,true,to)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(()->CashRegisterService.validatePeriod(from,to,true,to.minusSeconds(1))).isInstanceOf(BusinessRuleException.class);
        assertThatCode(()->CashRegisterService.validatePeriod(from,to,true,to)).doesNotThrowAnyException();
    }

    @Test void closureFingerprintNormalizesOrderAndAmountsButRetainsPeriodAndActor() {
        var a=new CloseCashRequest(from,to,cashier,List.of(new DeclaredAmount("cad",new BigDecimal("10.0")),new DeclaredAmount("USD",BigDecimal.ONE)),"Counted");
        var b=new CloseCashRequest(from,to,cashier,List.of(new DeclaredAmount("USD",new BigDecimal("1.00")),new DeclaredAmount("CAD",new BigDecimal("10.00"))),"Counted");
        assertThat(CashRegisterService.fingerprint(cashier,a)).isEqualTo(CashRegisterService.fingerprint(cashier,b));
        assertThat(CashRegisterService.fingerprint(other,a)).isNotEqualTo(CashRegisterService.fingerprint(cashier,a));
    }

    @Test void cashierCannotReadAnotherCashierReport() {
        var jdbc=mock(JdbcTemplate.class); var users=mock(CurrentUserContext.class); var licenses=mock(LicensingAccess.class);
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(cashier,org,Set.of("CASHIER")));
        when(jdbc.queryForObject("select count(*) from licensing.member where organization_id=? and person_id=? and role=? and status='ACTIVE'",Integer.class,org,cashier,"CASHIER")).thenReturn(1);
        var service=new CashRegisterService(jdbc,users,licenses,30);
        assertThatThrownBy(()->service.report(from,to,other)).isInstanceOf(AccessDeniedException.class);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }

    @Test void accountantMayNotCloseRegister() {
        var jdbc=mock(JdbcTemplate.class); var users=mock(CurrentUserContext.class);
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(cashier,org,Set.of("ACCOUNTANT")));
        when(jdbc.queryForList("select id from organization.organization where id=? for update",org)).thenReturn(List.of(Map.of("id",org)));
        var service=new CashRegisterService(jdbc,users,mock(LicensingAccess.class),30);
        assertThatThrownBy(()->service.close("closing-001",new CloseCashRequest(from,to,cashier,List.of(),null))).isInstanceOf(AccessDeniedException.class);
    }

    @Test void overlappingClosingIsRejectedBeforeAnySnapshotIsWritten() {
        var jdbc=mock(JdbcTemplate.class); var users=mock(CurrentUserContext.class);
        when(users.current()).thenReturn(new CurrentUserContext.UserContext(cashier,org,Set.of("PLATFORM_ADMIN")));
        when(jdbc.queryForList("select id from organization.organization where id=? for update",org)).thenReturn(List.of(Map.of("id",org)));
        when(jdbc.queryForObject(contains("select count(*) from identity.person"),eq(Integer.class),eq(cashier),eq(org),eq(org))).thenReturn(1);
        when(jdbc.queryForObject(contains("and period_start < ? and period_end > ?"),eq(Integer.class),eq(org),eq(cashier),any(),any())).thenReturn(1);
        var service=new CashRegisterService(jdbc,users,mock(LicensingAccess.class),30);
        assertThatThrownBy(()->service.close("closing-001",new CloseCashRequest(from,to,cashier,List.of(),null))).isInstanceOf(ConflictException.class);
        verify(jdbc,never()).update(anyString(),any(Object[].class));
    }

    private Receipt receipt(String type,String currency,String amount,UUID person) {
        UUID id=UUID.randomUUID();
        return new Receipt(id,"REC-"+id,UUID.randomUUID(),type,new BigDecimal(amount),currency,null,from,person);
    }
}
