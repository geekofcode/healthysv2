package org.novasos.healthysv2.lifecycle;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class DataDeletionTests {
  @AfterEach
  void clear() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void rejectsAnonymousAndMalformedSubjects() {
    var service = new DataDeletionService(mock(JdbcTemplate.class));
    assertThatThrownBy(service::owner).isInstanceOf(AccessDeniedException.class);
    var jwt = Jwt.withTokenValue("token").header("alg", "none").subject("not-a-uuid").build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    assertThatThrownBy(service::owner).isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void selfScopeUsesOnlySubjectRegardlessOfRolesOrSelectedOrganization() {
    UUID subject = UUID.randomUUID(), person = UUID.randomUUID();
    var jdbc = mock(JdbcTemplate.class);
    when(jdbc.queryForList(
            "select id from identity.person where keycloak_user_id=?", UUID.class, subject))
        .thenReturn(List.of(person));
    var jwt =
        Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(subject.toString())
            .claim("organization_id", UUID.randomUUID().toString())
            .build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    assertThat(new DataDeletionService(jdbc).owner()).isEqualTo(person);
    verifyNoMoreInteractionsAfterOwner(jdbc);
  }

  private void verifyNoMoreInteractionsAfterOwner(JdbcTemplate jdbc) {
    verify(jdbc)
        .queryForList(
            eq("select id from identity.person where keycloak_user_id=?"),
            eq(UUID.class),
            any(UUID.class));
    verifyNoMoreInteractions(jdbc);
  }

  @Test
  void idempotencyKeyRejectsBlankOversizedAndUnsafeInputs() {
    for (String key : Arrays.asList(null, "", " ", "x".repeat(129), "bad key", "a\n"))
      assertThatThrownBy(() -> DataDeletionService.validateKey(key))
          .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    assertThatCode(() -> DataDeletionService.validateKey("deletion:uuid-1"))
        .doesNotThrowAnyException();
  }

  @Test
  void policyRejectsAccidentalZeroOrNegativeRetention() {
    assertThatThrownBy(() -> new RetentionConfiguration(mock(JdbcTemplate.class), 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new RetentionConfiguration(mock(JdbcTemplate.class), 201))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void externalTableNamesSplitOnLiteralDot() {
    assertThat(EpisodeRetentionPurger.quotedTable("clinical.new_dependency"))
        .isEqualTo(DeletionWorker.quote("clinical") + "." + DeletionWorker.quote("new_dependency"));
  }

  @Test
  void catalogIdentifiersAreQuoted() {
    assertThat(DeletionWorker.quote("table\"name")).isEqualTo("\"table\"\"name\"");
  }
}
