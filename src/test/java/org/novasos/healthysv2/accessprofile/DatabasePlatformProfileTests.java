package org.novasos.healthysv2.accessprofile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.accessprofile.api.AccessProfileResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;

class DatabasePlatformProfileTests {
  private final JdbcTemplate db = mock(JdbcTemplate.class);
  private final UUID person = UUID.randomUUID(), subject = UUID.randomUUID();
  private final OwnedAccessProfileService service = new OwnedAccessProfileService(db);
  private final String administrators =
      "select person_id from accessprofile.platform_administrator where person_id=? and"
          + " status='ACTIVE'";

  @BeforeEach
  void account() {
    when(db.query(
            eq("select id from identity.person where keycloak_user_id=? and status='ACTIVE'"),
            org.mockito.ArgumentMatchers.<RowMapper<UUID>>any(),
            eq(subject)))
        .thenReturn(List.of(person));
  }

  @Test
  void keycloakAdministratorRoleCannotCreateAnAdministratorProfile() {
    assertTrue(service.profiles(subject, Set.of("PLATFORM_ADMIN")).isEmpty());
    assertThrows(
        AccessDeniedException.class,
        () ->
            service.resolve(
                subject, Set.of("PLATFORM_ADMIN"), AccessProfileResolver.platformId(person), null));
  }

  @Test
  void databaseAdministratorWorksWithoutAnyKeycloakRole() {
    when(db.queryForList(administrators, UUID.class, person)).thenReturn(List.of(person));
    var profile =
        service.resolve(subject, Set.of(), AccessProfileResolver.platformId(person), null);
    assertEquals("PLATFORM_ADMIN", profile.type());
    assertEquals(person, profile.personId());
    when(db.queryForObject(
            "select count(*) from identity.person where id=? and status='ACTIVE'",
            Integer.class,
            person))
        .thenReturn(1);
    assertEquals(profile, service.forPerson(person, profile.id()));
  }

  @Test
  void revocationTakesEffectWithTheSameUnexpiredTokenAndHistoricalRole() {
    when(db.queryForList(administrators, UUID.class, person))
        .thenReturn(List.of(person), List.of());
    UUID selected = AccessProfileResolver.platformId(person);
    assertEquals(
        "PLATFORM_ADMIN",
        service.resolve(subject, Set.of("PLATFORM_ADMIN"), selected, null).type());
    assertThrows(
        AccessDeniedException.class,
        () -> service.resolve(subject, Set.of("PLATFORM_ADMIN"), selected, null));
  }
}
