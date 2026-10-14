package org.novasos.healthysv2.identity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EmployeeAccountProvisioningTests {
  @Test
  void emailCanonicalizationPreventsCaseAndWhitespaceDuplicates() {
    assertEquals(
        "doctor@example.org",
        EmployeeAccountProvisioningService.normalizeEmail("  Doctor@Example.ORG  "));
  }

  @Test
  void rejectsMissingAndMalformedEmail() {
    assertThrows(
        IllegalArgumentException.class,
        () -> EmployeeAccountProvisioningService.normalizeEmail(null));
    assertThrows(
        IllegalArgumentException.class,
        () -> EmployeeAccountProvisioningService.normalizeEmail("doctor example.org"));
  }

  @Test
  void exactRemoteEmailDoesNotAuthorizeLinkingAnUnrelatedAccount() {
    UUID person = UUID.randomUUID();
    assertFalse(
        StaffAccountProvisioningWorker.owned(
            Map.of("email", "doctor@example.org"), person, "doctor@example.org"));
    assertFalse(
        StaffAccountProvisioningWorker.owned(
            Map.of(
                "email",
                "doctor@example.org",
                "attributes",
                Map.of("healthysProvisioningPersonId", List.of(UUID.randomUUID().toString()))),
            person,
            "doctor@example.org"));
  }

  @Test
  void onlyOurSingleMatchingCorrelationAndEmailCanBeReconciled() {
    UUID person = UUID.randomUUID();
    Map<String, Object> user =
        Map.of(
            "email",
            "Doctor@example.org",
            "attributes",
            Map.of("healthysProvisioningPersonId", List.of(person.toString())));
    assertTrue(StaffAccountProvisioningWorker.owned(user, person, "doctor@example.org"));
    assertFalse(StaffAccountProvisioningWorker.owned(user, person, "other@example.org"));
  }

  @Test
  void ambiguousExistingEmailRequiresReconciliationBeforeCreation() {
    var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
    var first =
        new org.novasos.healthysv2.identity.api.EmployeeAccountProvisioning.AccountIdentity(
            UUID.randomUUID(), "One", "Doctor", "doctor@example.org", "ACTIVE", "ACTIVE");
    var second =
        new org.novasos.healthysv2.identity.api.EmployeeAccountProvisioning.AccountIdentity(
            UUID.randomUUID(), "Two", "Doctor", "doctor@example.org", "ACTIVE", "ACTIVE");
    org.mockito.Mockito.doReturn(List.of(first, second))
        .when(jdbc)
        .query(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.RowMapper.class),
            org.mockito.ArgumentMatchers.eq("doctor@example.org"));
    var service = new EmployeeAccountProvisioningService(jdbc);
    assertThrows(
        org.novasos.healthysv2.shared.api.error.ConflictException.class,
        () -> service.ensureAccount("New", "Doctor", "doctor@example.org"));
    org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.never())
        .update(
            org.mockito.ArgumentMatchers.contains("insert into identity.person"),
            org.mockito.ArgumentMatchers.any(Object[].class));
  }

  @Test
  void inactiveExistingIdentityCannotBeReactivatedByAnInvitation() {
    var jdbc = org.mockito.Mockito.mock(org.springframework.jdbc.core.JdbcTemplate.class);
    var inactive =
        new org.novasos.healthysv2.identity.api.EmployeeAccountProvisioning.AccountIdentity(
            UUID.randomUUID(), "One", "Doctor", "doctor@example.org", "INACTIVE", "ACTIVE");
    org.mockito.Mockito.doReturn(List.of(inactive))
        .when(jdbc)
        .query(
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.RowMapper.class),
            org.mockito.ArgumentMatchers.eq("doctor@example.org"));
    assertThrows(
        org.novasos.healthysv2.shared.api.error.ConflictException.class,
        () ->
            new EmployeeAccountProvisioningService(jdbc)
                .ensureAccount("New", "Doctor", "doctor@example.org"));
  }
}
