package org.novasos.healthysv2.accessprofile;

import java.util.UUID;
import org.novasos.healthysv2.accessprofile.api.PlatformAdministratorProvisioning;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class ConfiguredPlatformAdministratorProvisioning implements PlatformAdministratorProvisioning {
  private final JdbcTemplate jdbc;
  private final UUID configuredSubject;

  ConfiguredPlatformAdministratorProvisioning(
      JdbcTemplate jdbc, @Value("${PLATFORM_ADMIN_BOOTSTRAP_SUBJECT:}") String configuredSubject) {
    this.jdbc = jdbc;
    this.configuredSubject =
        configuredSubject == null || configuredSubject.isBlank()
            ? null
            : UUID.fromString(configuredSubject.trim());
  }

  @Override
  public void bootstrap(UUID subject) {
    if (configuredSubject == null || !configuredSubject.equals(subject)) return;
    // Safe after identity provisioning; no existing or revoked grant can be overwritten.
    jdbc.update(
        """
        insert into accessprofile.platform_administrator(person_id,status,grant_source)
        select id,'ACTIVE','CONFIGURED_BOOTSTRAP' from identity.person
        where keycloak_user_id=? and status='ACTIVE'
        on conflict(person_id) do nothing
        """,
        subject);
  }
}
