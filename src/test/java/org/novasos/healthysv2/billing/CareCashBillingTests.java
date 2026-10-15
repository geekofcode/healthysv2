package org.novasos.healthysv2.billing;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.novasos.healthysv2.billing.api.CareBillingDtos.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.care.api.CareSettlement;
import org.novasos.healthysv2.licensing.api.LicensingAccess;
import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.shared.api.error.ConflictException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

class CareCashBillingTests {
  private final UUID org = UUID.randomUUID(),
      episode = UUID.randomUUID(),
      person = UUID.randomUUID();
  private JdbcTemplate jdbc;
  private InvoiceRepository invoices;
  private PaymentRepository payments;
  private CareSettlement settlement;
  private CareBillingService service;
  private Invoice invoice;
  private CurrentUserContext users;
  private org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess permissions;

  @BeforeEach
  void setup() {
    jdbc = mock(JdbcTemplate.class);
    invoices = mock(InvoiceRepository.class);
    payments = mock(PaymentRepository.class);
    settlement = mock(CareSettlement.class);
    users = mock(CurrentUserContext.class);
    invoice = issued();
    service =
        new CareBillingService(
            jdbc,
            invoices,
            payments,
            mock(BillingService.class),
            users,
            mock(LicensingAccess.class),
            settlement,
            30);
    permissions =
        mock(org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess.class);
    service.configurePermissions(permissions);
    when(users.current())
        .thenReturn(new CurrentUserContext.UserContext(person, org, Set.of("CASHIER")));
    when(jdbc.queryForList(
            "select episode_id,organization_id from billing.care_invoice where invoice_id=?",
            invoice.getId()))
        .thenReturn(List.of(Map.of("episode_id", episode, "organization_id", org)));
    when(jdbc.queryForList("select id from organization.organization where id=? for update", org))
        .thenReturn(List.of(Map.of("id", org)));
    when(jdbc.queryForObject(
            "select count(*) from licensing.member where organization_id=? and person_id=? and"
                + " role='CASHIER' and status='ACTIVE'",
            Integer.class,
            org,
            person))
        .thenReturn(1);
    var row = new HashMap<String, Object>();
    row.put("patient_id", invoice.getPatientId());
    row.put("status", "ACTIVE");
    row.put("started_at", null);
    when(jdbc.queryForList(
            "select"
                + " patient_id,status,started_at,tariff_id,tariff_price,tariff_currency,tariff_tax_rate"
                + " from care.care_episode where id=? and organization_id=? for update",
            episode,
            org))
        .thenReturn(List.of(row));
    when(invoices.lock(invoice.getId())).thenReturn(Optional.of(invoice));
    when(payments.saveAndFlush(any(Payment.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void cataloguePriceAndTaxAreFrozenDespiteLaterChanges() {
    var invoice = issued();
    assertThat(invoice.getTotalAmount()).isEqualByComparingTo("110.00");
    assertThat(invoice.getItems().getFirst().getUnitPrice()).isEqualByComparingTo("100.00");
    assertThat(
            CareBillingService.tax(
                new BigDecimal("1.125"), new BigDecimal("7.99"), new BigDecimal("14.975")))
        .isEqualByComparingTo("1.35");
    assertThatThrownBy(
            () ->
                invoice.addItem(
                    "CARE_TARIFF", null, "tamper", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void partialCashDoesNotUnlockButFinalSettlementDoes() {
    service.cash(invoice.getId(), "partial-001", new CashRequest(new BigDecimal("40"), "CAD"));
    assertThat(invoice.balance()).isEqualByComparingTo("70.00");
    verifyNoInteractions(settlement);
    service.cash(invoice.getId(), "remaining-002", new CashRequest(new BigDecimal("70"), "CAD"));
    assertThat(invoice.getStatus()).isEqualTo("PAID");
    verify(settlement).markSettled(episode, org);
  }

  @Test
  void refundRestoresBalanceAndRevokesEligibility() {
    invoice.recordPayment(new BigDecimal("110"), "CAD");
    var receipt =
        service.refund(
            invoice.getId(),
            "refund-001",
            new RefundRequest(new BigDecimal("10"), "CAD", "Patient correction"));
    assertThat(receipt.operationType()).isEqualTo("REFUND");
    assertThat(receipt.cashierPersonId()).isEqualTo(person);
    assertThat(invoice.balance()).isEqualByComparingTo("10");
    verify(settlement).reopenPayment(episode, org);
  }

  @Test
  void refundAfterFirstEncounterIsRejectedWithoutBalanceChange() {
    invoice.recordPayment(new BigDecimal("110"), "CAD");
    when(jdbc.queryForList(
            "select"
                + " patient_id,status,started_at,tariff_id,tariff_price,tariff_currency,tariff_tax_rate"
                + " from care.care_episode where id=? and organization_id=? for update",
            episode,
            org))
        .thenReturn(
            List.of(
                Map.of(
                    "patient_id",
                    invoice.getPatientId(),
                    "status",
                    "ACTIVE",
                    "started_at",
                    Instant.now())));
    assertThatThrownBy(
            () ->
                service.refund(
                    invoice.getId(),
                    "refund-002",
                    new RefundRequest(BigDecimal.ONE, "CAD", "Mistake")))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("first medical encounter");
    assertThat(invoice.getAmountPaid()).isEqualByComparingTo("110");
    verifyNoInteractions(settlement);
  }

  @Test
  void replayWithChangedAmountDoesNotCaptureTwice() {
    when(jdbc.queryForList(
            "select fingerprint from billing.cash_operation where invoice_id=? and"
                + " idempotency_key=?",
            invoice.getId(),
            "payment-001"))
        .thenReturn(List.of(Map.of("fingerprint", "PAYMENT|40.00|CAD|")));
    assertThatThrownBy(
            () ->
                service.cash(
                    invoice.getId(), "payment-001", new CashRequest(new BigDecimal("50"), "CAD")))
        .isInstanceOf(ConflictException.class);
    assertThat(invoice.getAmountPaid()).isEqualByComparingTo("0");
    verifyNoInteractions(payments, settlement);
  }

  @Test
  void platformAdministratorAloneCannotConfirmCash() {
    when(users.current())
        .thenReturn(new CurrentUserContext.UserContext(person, null, Set.of("PLATFORM_ADMIN")));
    assertThatThrownBy(
            () ->
                service.cash(
                    invoice.getId(), "payment-001", new CashRequest(BigDecimal.ONE, "CAD")))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(payments, settlement);
  }

  @Test
  void staleCashierAuthorityCannotCollectAfterRoleChange() {
    doThrow(new AccessDeniedException("ORGANIZATION_PERMISSION_DENIED"))
        .when(permissions)
        .require(org, "payments", "CREATE");
    assertThatThrownBy(
            () ->
                service.cash(
                    invoice.getId(), "payment-001", new CashRequest(BigDecimal.ONE, "CAD")))
        .isInstanceOf(AccessDeniedException.class);
    verifyNoInteractions(payments, settlement);
  }

  @Test
  void fingerprintsNormalizeEquivalentAmountsAndRetainOperationType() {
    assertThat(
            CareBillingService.operationFingerprint("PAYMENT", new BigDecimal("1.0"), "cad", null))
        .isEqualTo(
            CareBillingService.operationFingerprint(
                "PAYMENT", new BigDecimal("1.00"), "CAD", null));
    assertThat(
            CareBillingService.operationFingerprint("REFUND", BigDecimal.ONE, "CAD", "Correction"))
        .isNotEqualTo(
            CareBillingService.operationFingerprint("PAYMENT", BigDecimal.ONE, "CAD", null));
    assertThatThrownBy(() -> CareBillingService.validateKey("tiny"))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void refundCannotExceedCollectedCashOrChangeCurrency() {
    invoice.recordPayment(new BigDecimal("40"), "CAD");
    assertThatThrownBy(() -> invoice.refund(new BigDecimal("41"), "CAD"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> invoice.refund(new BigDecimal("10"), "USD"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(invoice.getAmountPaid()).isEqualByComparingTo("40");
  }

  @Test
  void legacyCashRouteCannotBypassIdempotencyOrCashierChecks() {
    var legacy = new BillingService(invoices, payments, jdbc, users);
    legacy.configurePermissions(
        mock(org.novasos.healthysv2.organizationaccess.api.OrganizationPermissionAccess.class));
    assertThatThrownBy(
            () ->
                legacy.recordPayment(
                    invoice.getId(),
                    new org.novasos.healthysv2.billing.api.BillingDtos.RecordPaymentRequest(
                        BigDecimal.ONE, "CAD", "CASH", null, null)))
        .isInstanceOf(ConflictException.class)
        .hasMessageContaining("Idempotency-Key");
    verifyNoInteractions(payments, settlement);
  }

  private Invoice issued() {
    var invoice = Invoice.create(UUID.randomUUID(), org, null, "CAD", null);
    invoice.addItem(
        "CARE_TARIFF",
        UUID.randomUUID(),
        "Consultation",
        BigDecimal.ONE,
        new BigDecimal("100"),
        new BigDecimal("10"));
    invoice.issue();
    return invoice;
  }
}
