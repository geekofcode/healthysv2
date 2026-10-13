package org.novasos.healthysv2.licensing;

import static org.assertj.core.api.Assertions.*;

import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.novasos.healthysv2.licensing.api.LicensingDtos.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class LicensingConcurrencyIntegrationTests {
  @Autowired JdbcTemplate db;
  @Autowired LicensingService service;

  private void authenticate(UUID subject, String role) {
    var jwt = Jwt.withTokenValue("test").header("alg", "none").subject(subject.toString()).build();
    SecurityContextHolder.getContext()
        .setAuthentication(
            new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
  }

  private UUID organization() {
    UUID org = UUID.randomUUID();
    db.update(
        "insert into organization.organization(id,organization_number,name) values(?,?,?)",
        org,
        "LIC-" + org,
        "License test");
    db.update(
        "insert into"
            + " licensing.subscription(organization_id,status,period_start,period_end,doctor_quota)"
            + " values(?,'active',now()-interval '1 day',now()+interval '1 month',1)",
        org);
    return org;
  }

  private void cleanup(UUID org) {
    SecurityContextHolder.clearContext();
    db.update("delete from licensing.audit_entry where organization_id=?", org);
    db.update("delete from licensing.member where organization_id=?", org);
    db.update("delete from licensing.subscription where organization_id=?", org);
    db.update("delete from organization.organization where id=?", org);
  }

  @Test
  void concurrentInvitationsCannotOverbookLastSeat() throws Exception {
    UUID org = organization();
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      CountDownLatch start = new CountDownLatch(1);
      List<Future<Boolean>> jobs = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        String email = "test" + i + "@example.org";
        jobs.add(
            executor.submit(
                () -> {
                  authenticate(UUID.randomUUID(), "PLATFORM_ADMIN");
                  start.await();
                  try {
                    service.invite(org, new TeamRequest(null, email, "DOCTOR"));
                    return true;
                  } catch (IllegalArgumentException e) {
                    assertThat(e.getMessage()).contains("QUOTA_EXCEEDED");
                    return false;
                  } finally {
                    SecurityContextHolder.clearContext();
                  }
                }));
      }
      start.countDown();
      int successful = 0;
      for (var job : jobs) if (job.get(20, TimeUnit.SECONDS)) successful++;
      assertThat(successful).isEqualTo(1);
    } finally {
      executor.shutdownNow();
      cleanup(org);
    }
  }

  @Test
  void globalHospitalAdminCannotManageUnrelatedOrganization() {
    UUID org = organization();
    try {
      authenticate(UUID.randomUUID(), "HOSPITAL_ADMIN");
      assertThatThrownBy(() -> service.team(org))
          .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    } finally {
      cleanup(org);
    }
  }

  @Test
  void invitationAcceptanceRechecksDowngradedQuota() {
    UUID org = organization(), subject = UUID.randomUUID(), person = UUID.randomUUID();
    try {
      db.update(
          "insert into identity.person(id,person_number,keycloak_user_id,first_name,last_name)"
              + " values(?,?,?,'Invited','Doctor')",
          person,
          "LIC-" + person,
          subject);
      authenticate(UUID.randomUUID(), "PLATFORM_ADMIN");
      var invitation = service.invite(org, new TeamRequest(person, null, "DOCTOR"));
      db.update("update licensing.subscription set doctor_quota=0 where organization_id=?", org);
      authenticate(subject, "DOCTOR");
      assertThatThrownBy(() -> service.accept(org, (UUID) invitation.get("id")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("QUOTA_EXCEEDED");
      assertThat(
              db.queryForObject(
                  "select status from licensing.member where id=?",
                  String.class,
                  invitation.get("id")))
          .isEqualTo("INVITED");
    } finally {
      cleanup(org);
      db.update("delete from identity.person where id=?", person);
    }
  }

  @Test
  void clinicalActivationCannotCreateAnUnverifiedProfessional() {
    UUID org = organization(), person = UUID.randomUUID();
    try {
      db.update(
          "insert into identity.person(id,person_number,first_name,last_name)"
              + " values(?,?,'Not','Verified')",
          person,
          "LIC-" + person);
      authenticate(UUID.randomUUID(), "PLATFORM_ADMIN");
      var invitation = service.invite(org, new TeamRequest(person, null, "DOCTOR"));
      assertThatThrownBy(
              () ->
                  service.update(
                      org, (UUID) invitation.get("id"), new TeamUpdate("DOCTOR", "ACTIVE")))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("VERIFIED_PROFESSIONAL_PROFILE_REQUIRED");
      assertThat(
              db.queryForObject(
                  "select status from licensing.member where id=?",
                  String.class,
                  invitation.get("id")))
          .isEqualTo("INVITED");
    } finally {
      cleanup(org);
      db.update("delete from identity.person where id=?", person);
    }
  }
}
