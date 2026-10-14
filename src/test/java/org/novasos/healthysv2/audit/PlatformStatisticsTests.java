package org.novasos.healthysv2.audit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;

class PlatformStatisticsTests {
  @Test
  void unknownEstablishmentReturnsNotFoundInsteadOfFabricatedStatistics() {
    var jdbc = mock(JdbcTemplate.class);
    var org = UUID.randomUUID();
    when(jdbc.queryForObject(
            "select count(*) from organization.organization where id=?", Long.class, org))
        .thenReturn(0L);
    assertThatThrownBy(() -> new PlatformStatisticsController(jdbc).organization(org))
        .isInstanceOf(ResourceNotFoundException.class);
    verifyNoMoreInteractionsAfterLookup(jdbc, org);
  }

  private void verifyNoMoreInteractionsAfterLookup(JdbcTemplate jdbc, UUID org) {
    verify(jdbc)
        .queryForObject(
            "select count(*) from organization.organization where id=?", Long.class, org);
    verifyNoMoreInteractions(jdbc);
  }

  @Test
  void responseContainsOnlyCountsAndTheRequestedOrganizationIdentifier() {
    var names =
        java.util.Arrays.stream(PlatformStatisticsController.Statistics.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName)
            .toList();
    assertThat(names)
        .containsExactly(
            "organizationId",
            "organizations",
            "patients",
            "employees",
            "episodes",
            "visits",
            "completedVisits",
            "documents",
            "invoices",
            "cashOperations");
    assertThat(
            java.util.Arrays.stream(
                    PlatformStatisticsController.Statistics.class.getRecordComponents())
                .skip(1)
                .map(java.lang.reflect.RecordComponent::getType))
        .allMatch(t -> t == long.class);
  }
}
