package org.novasos.healthysv2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.novasos.healthysv2.accessprofile.api.AccessProfileResolver;
import org.novasos.healthysv2.profilecontract.api.ContractAccess;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest(
    properties = {
      "STAFF_ACCOUNT_PROVISIONING_ENABLED=false",
      "DATA_DELETION_WORKER_ENABLED=false",
      "DATA_RETENTION_PURGE_ENABLED=false",
      "AI_WORKER_ENABLED=false"
    })
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, OrganizationRolesIntegrationTests.Tokens.class})
class OrganizationRolesIntegrationTests {
  static final Map<String, UUID> subjects = new HashMap<>();
  UUID org, foreign, managerPerson, managerMember, hospitalProfile, platformProfile, platformPerson;
  @MockitoBean ContractAccess contracts;
  @Autowired MockMvc mvc;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;

  @BeforeEach
  void setup() {
    subjects.clear();
    UUID admin = person("hospital"), platform = person("platform");
    managerPerson = person("manager");
    org = organization(admin);
    foreign = organization(platform);
    hospitalProfile = member(org, admin, "HOSPITAL_ADMIN");
    platformProfile = AccessProfileResolver.platformId(platform);
    platformPerson = platform;
    db.update(
        "insert into accessprofile.platform_administrator(person_id,status) values (?,'ACTIVE')",
        platform);
    managerMember = member(org, managerPerson, "CASHIER");
  }

  UUID person(String token) {
    UUID id = UUID.randomUUID(), subject = UUID.randomUUID();
    subjects.put(token, subject);
    db.update(
        "insert into identity.person(id,person_number,keycloak_user_id,first_name,last_name,status)"
            + " values(?,?,?,'Test','Account','ACTIVE')",
        id,
        "T-" + id,
        subject);
    return id;
  }

  UUID organization(UUID administrator) {
    UUID id = UUID.randomUUID();
    db.update(
        "insert into"
            + " organization.organization(id,organization_number,name,administrator_person_id,status,timezone)"
            + " values(?,?,'Role clinic',?,'ACTIVE','UTC')",
        id,
        "ORG-" + id,
        administrator);
    db.update(
        "insert into"
            + " licensing.subscription(organization_id,status,period_start,period_end,doctor_quota,professional_quota,cashier_quota,accountant_quota)"
            + " values(?,'active',now()-interval '1 day',now()+interval '30 days',5,5,5,5)",
        id);
    return id;
  }

  UUID member(UUID organization, UUID person, String role) {
    UUID id = UUID.randomUUID();
    db.update(
        "insert into licensing.member(id,organization_id,person_id,role,status)"
            + " values(?,?,?,?,'ACTIVE')",
        id,
        organization,
        person,
        role);
    return id;
  }

  String auth(String token) {
    return "Bearer " + token;
  }

  String role() throws Exception {
    return mvc.perform(
            post("/api/v1/organizations/{org}/access/roles", org)
                .header("Authorization", auth("hospital"))
                .header("X-Profile-ID", hospitalProfile)
                .header("X-Organization-ID", org)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"name\":\"Lecture"
                        + " caisse\",\"permissions\":[{\"resource\":\"cashreports\",\"actions\":[\"READ\"]}]}"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  void selectedPlatformProfileNeverGetsHospitalOperations() throws Exception {
    for (String path :
        List.of(
            "/api/v1/patients",
            "/api/v1/professionals",
            "/api/v1/documents",
            "/api/v1/invoices",
            "/api/v1/licensing/organizations/" + org + "/team",
            "/api/v1/organizations/" + org + "/access/roles")) {
      mvc.perform(
              get(path)
                  .header("Authorization", auth("platform"))
                  .header("X-Profile-ID", platformProfile))
          .andExpect(status().isForbidden());
    }
    mvc.perform(
            get("/api/v1/admin/organizations/{org}/statistics", org)
                .header("Authorization", auth("platform"))
                .header("X-Profile-ID", platformProfile))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.organizationId").value(org.toString()))
        .andExpect(jsonPath("$.employees").isNumber())
        .andExpect(jsonPath("$.patients").isNumber())
        .andExpect(jsonPath("$.firstName").doesNotExist());
  }

  @Test
  void customReadOnlyCashRoleIsEnforcedAndDeletingItDoesNotRestoreLegacyCashierRights()
      throws Exception {
    UUID roleId = UUID.fromString(json.readTree(role()).path("id").asText());
    mvc.perform(
            put("/api/v1/organizations/{org}/access/members/{member}/roles", org, managerMember)
                .header("Authorization", auth("hospital"))
                .header("X-Profile-ID", hospitalProfile)
                .header("X-Organization-ID", org)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleIds\":[\"" + roleId + "\"]}"))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/billing/care/cash-report")
                .param("from", Instant.now().minusSeconds(3600).toString())
                .param("to", Instant.now().plusSeconds(60).toString())
                .header("Authorization", auth("manager"))
                .header("X-Profile-ID", managerMember)
                .header("X-Organization-ID", org))
        .andExpect(status().isOk());
    mvc.perform(
            post("/api/v1/billing/care/invoices/{id}/cash", UUID.randomUUID())
                .header("Authorization", auth("manager"))
                .header("X-Profile-ID", managerMember)
                .header("X-Organization-ID", org)
                .header("Idempotency-Key", "test-readonly")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
    mvc.perform(
            delete("/api/v1/organizations/{org}/access/roles/{role}", org, roleId)
                .header("Authorization", auth("hospital"))
                .header("X-Profile-ID", hospitalProfile)
                .header("X-Organization-ID", org))
        .andExpect(status().isOk());
    mvc.perform(
            get("/api/v1/billing/care/cash-report")
                .param("from", Instant.now().minusSeconds(3600).toString())
                .param("to", Instant.now().plusSeconds(60).toString())
                .header("Authorization", auth("manager"))
                .header("X-Profile-ID", managerMember)
                .header("X-Organization-ID", org))
        .andExpect(status().isForbidden());
  }

  @Test
  void creatingOrganizationCreatesAnAdministratorAccountAndDurableProvisioningRequest()
      throws Exception {
    String suffix = UUID.randomUUID().toString();
    var result =
        mvc.perform(
                post("/api/v1/organizations")
                    .header("Authorization", auth("platform"))
                    .header("X-Profile-ID", platformProfile)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"number\":\"NEW-"
                            + suffix
                            + "\",\"name\":\"New"
                            + " clinic\",\"administratorAccount\":{\"firstName\":\"Local\",\"lastName\":\"Admin\",\"email\":\""
                            + suffix
                            + "@example.test\"}}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.departments").isEmpty())
            .andExpect(jsonPath("$.rooms").isEmpty())
            .andReturn();
    var body = json.readTree(result.getResponse().getContentAsString());
    UUID createdOrg = UUID.fromString(body.path("id").asText()),
        person = UUID.fromString(body.path("administratorPersonId").asText());
    assertThat(
            db.queryForObject(
                "select count(*) from licensing.member where organization_id=? and person_id=? and"
                    + " role='HOSPITAL_ADMIN' and status='ACTIVE'",
                Integer.class,
                createdOrg,
                person))
        .isEqualTo(1);
    assertThat(
            db.queryForObject(
                "select status from identity.staff_account_outbox where person_id=?",
                String.class,
                person))
        .isEqualTo("PENDING");
  }

  @Test
  void creatingEmployeeIsAtomicWithItsRolePolicyAndRejectsForeignRoleAssignment() throws Exception {
    String suffix = UUID.randomUUID().toString();
    UUID roleId = UUID.fromString(json.readTree(role()).path("id").asText());
    String body =
        "{\"firstName\":\"New\",\"lastName\":\"Employee\",\"email\":\""
            + suffix
            + "@example.test\",\"role\":\"CASHIER\",\"customRoleIds\":[\""
            + roleId
            + "\"]}";
    var result =
        mvc.perform(
                post("/api/v1/licensing/organizations/{org}/employees", org)
                    .header("Authorization", auth("hospital"))
                    .header("X-Profile-ID", hospitalProfile)
                    .header("X-Organization-ID", org)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isOk())
            .andReturn();
    UUID member =
        UUID.fromString(
            json.readTree(result.getResponse().getContentAsString()).path("id").asText());
    assertThat(
            db.queryForObject(
                "select count(*) from organizationaccess.member_policy where member_id=? and"
                    + " organization_id=?",
                Integer.class,
                member,
                org))
        .isEqualTo(1);
    assertThat(
            db.queryForObject(
                "select count(*) from organizationaccess.member_role where member_id=? and"
                    + " role_id=?",
                Integer.class,
                member,
                roleId))
        .isEqualTo(1);
    UUID foreignMember = member(foreign, managerPerson, "CASHIER");
    // Foreign member exists with a real person, but assigning it to this tenant must fail.
    mvc.perform(
            put("/api/v1/organizations/{org}/access/members/{member}/roles", org, foreignMember)
                .header("Authorization", auth("hospital"))
                .header("X-Profile-ID", hospitalProfile)
                .header("X-Organization-ID", org)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roleIds\":[\"" + roleId + "\"]}"))
        .andExpect(status().is4xxClientError());
    assertThat(
            db.queryForObject(
                "select count(*) from organizationaccess.member_policy where member_id=?",
                Integer.class,
                foreignMember))
        .isZero();
  }

  @Test
  void persistedPlatformAdministratorDoesNotNeedAnyRealmRole() throws Exception {
    subjects.put("platform-no-realm", subjects.get("platform"));
    mvc.perform(
            get("/api/v1/admin/statistics")
                .header("Authorization", auth("platform-no-realm"))
                .header("X-Profile-ID", platformProfile))
        .andExpect(status().isOk());
  }

  @Test
  void revocationCannotBeOverriddenByARealmAdminRole() throws Exception {
    db.update(
        "update accessprofile.platform_administrator set status='REVOKED',revoked_at=now() where"
            + " person_id=?",
        platformPerson);
    mvc.perform(
            get("/api/v1/admin/statistics")
                .header("Authorization", auth("platform"))
                .header("X-Profile-ID", platformProfile))
        .andExpect(status().isForbidden());
    assertThat(
            db.queryForObject(
                "select status from accessprofile.platform_administrator where person_id=?",
                String.class,
                platformPerson))
        .isEqualTo("REVOKED");
  }

  @TestConfiguration
  static class Tokens {
    @Bean
    JwtDecoder decoder() {
      return token -> {
        UUID subject = subjects.get(token);
        if (subject == null) throw new JwtException("Unknown test token");
        return Jwt.withTokenValue(token)
            .header("alg", "none")
            .subject(subject.toString())
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .claim(
                "realm_access",
                Map.of("roles", token.equals("platform") ? List.of("admin") : List.of()))
            .build();
      };
    }
  }
}
