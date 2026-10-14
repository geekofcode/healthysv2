package org.novasos.healthysv2.accessprofile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PlatformAdministratorProvisioningTests {
  private final JdbcTemplate jdbc = mock(JdbcTemplate.class);

  @Test
  void emptyConfigurationNeverGrantsPlatformAccess() {
    new ConfiguredPlatformAdministratorProvisioning(jdbc, "").bootstrap(UUID.randomUUID());
    verifyNoInteractions(jdbc);
  }

  @Test
  void anotherAuthenticatedSubjectCannotUseTheBootstrap() {
    new ConfiguredPlatformAdministratorProvisioning(jdbc, UUID.randomUUID().toString())
        .bootstrap(UUID.randomUUID());
    verifyNoInteractions(jdbc);
  }

  @Test
  void configuredSubjectCanOnlyInsertAnActiveLinkedPersonWithoutOverwritingRevocation() {
    UUID subject = UUID.randomUUID();
    new ConfiguredPlatformAdministratorProvisioning(jdbc, subject.toString()).bootstrap(subject);
    verify(jdbc)
        .update(
            argThat(
                sql ->
                    sql.contains("where keycloak_user_id=? and status='ACTIVE'")
                        && sql.contains("on conflict(person_id) do nothing")),
            eq(subject));
    verifyNoMoreInteractions(jdbc);
  }

  @Test
  void malformedBootstrapConfigurationFailsFast() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new ConfiguredPlatformAdministratorProvisioning(jdbc, "not-a-uuid"));
    verifyNoInteractions(jdbc);
  }
}
