package org.novasos.healthysv2.profilecontract;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.junit.jupiter.api.*;
import org.novasos.healthysv2.accessprofile.api.AccessProfileResolver;
import org.novasos.healthysv2.profilecontract.api.ContractDtos.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ContractHtmlTemplateServiceTests {
  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void administratorCreationPersistsSanitizedHtmlWithExplicitFormat() {
    UUID subject = UUID.randomUUID(), actor = UUID.randomUUID();
    var jwt = Jwt.withTokenValue("test").header("alg", "none").subject(subject.toString()).build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))));
    JdbcTemplate db = mock(JdbcTemplate.class);
    when(db.queryForList(contains("keycloak_user_id=?"), eq(UUID.class), eq(subject)))
        .thenReturn(List.of(actor));
    when(db.queryForObject(contains("coalesce(max(version)"), eq(Integer.class), eq("PATIENT")))
        .thenReturn(1);
    var service =
        new ProfileContractService(
            db,
            mock(AccessProfileResolver.class),
            mock(ContractObjectStorage.class),
            "healthys-backend");
    var request =
        new TemplateRequest(
            "PATIENT",
            "https://example.test/terms",
            "1",
            "<h2>Contrat</h2><p onclick='bad()'>{{signerName}}</p><p>{{signature}}</p><a"
                + " href='https://example.test/terms' style='color:red'>Conditions</a>",
            "HTML",
            "<p onclick='bad()'>{{signerName}}</p>",
            "<p>Page {{pageNumber}}/{{pageCount}}</p>");
    var saved = service.createTemplate(request);
    assertThat(saved.bodyFormat()).isEqualTo("HTML");
    assertThat(saved.bodyText())
        .contains(
            "<h2>Contrat</h2>",
            "{{signerName}}",
            "{{signature}}",
            "href=\"https://example.test/terms\"")
        .doesNotContain("onclick", "style=");
    assertThat(saved.headerHtml()).isEqualTo("<p>{{signerName}}</p>");
    assertThat(saved.footerHtml()).isEqualTo("<p>Page {{pageNumber}}/{{pageCount}}</p>");
    verify(db)
        .update(
            contains("body_format"),
            eq(saved.id()),
            eq("PATIENT"),
            eq(1),
            eq(request.termsUrl()),
            eq("1"),
            eq(saved.bodyText()),
            eq(actor),
            eq("HTML"),
            eq(saved.headerHtml()),
            eq(saved.footerHtml()));
  }
}
