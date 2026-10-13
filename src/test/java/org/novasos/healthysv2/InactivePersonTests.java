package org.novasos.healthysv2;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.mock.web.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class InactivePersonTests {
  final JdbcTemplate db = mock(JdbcTemplate.class);

  @AfterEach
  void reset() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void survivingIdpTokenDoesNotRestoreDeletedLocalAccountAccess() {
    UUID subject = UUID.randomUUID();
    var jwt = Jwt.withTokenValue("test").header("alg", "none").subject(subject.toString()).build();
    SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    when(db.query(anyString(), any(ResultSetExtractor.class), eq(subject))).thenReturn("INACTIVE");
    assertThrows(
        AccessDeniedException.class,
        () ->
            new InactivePersonWebConfiguration.Guard(db)
                .preHandle(
                    new MockHttpServletRequest("GET", "/api/v1/patients/me"),
                    new MockHttpServletResponse(),
                    new Object()));
  }
}
