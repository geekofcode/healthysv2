package org.novasos.healthysv2.audit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.*;

class PlatformActivityTests {
  @Test
  void platformAuditCannotSelectHospitalEventsOrPayloads() {
    var db = mock(JdbcTemplate.class);
    new PlatformActivityController(db).audit(99999);
    var sql = ArgumentCaptor.forClass(String.class);
    verify(db).query(sql.capture(), any(RowMapper.class), eq(500));
    assertThat(sql.getValue())
        .contains(
            "where action in ('PLAN_UPDATED','PLAN_CREATED','PLAN_DISABLED')",
            "entity_type='Organization'",
            "entity_type='ContractTemplate'")
        .doesNotContain(
            "old_value", "new_value", "EMPLOYEE_CREATED", "patient_id", "data_access_log");
  }

  @Test
  void authenticationLogOmitsNetworkAndDetailsPayload() {
    var db = mock(JdbcTemplate.class);
    new PlatformActivityController(db).authentication(-1);
    var sql = ArgumentCaptor.forClass(String.class);
    verify(db).query(sql.capture(), any(RowMapper.class), eq(1));
    assertThat(sql.getValue())
        .doesNotContain("details", "ip_address", "user_agent", "keycloak_user_id");
  }
}
