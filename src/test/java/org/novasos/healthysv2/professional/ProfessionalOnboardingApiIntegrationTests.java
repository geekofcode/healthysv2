package org.novasos.healthysv2.professional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
@ActiveProfiles("test")
@SpringBootTest(properties= {
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://keycloak.example/realms/healthys","spring.security.oauth2.resourceserver.jwt.audiences=healthys-backend-apps","healthys.security.api-client-id=healthys-backend-apps","healthys.security.cors.allowed-origins=http://localhost:5173","healthys.professional-role-sync.enabled=false"
}
) @AutoConfigureMockMvc @Import( {
    TestcontainersConfiguration.class,ProfessionalOnboardingApiIntegrationTests.JwtFixtures.class
}
) class ProfessionalOnboardingApiIntegrationTests  {
    private static final UUID APPLICANT=UUID.randomUUID(),OTHER=UUID.randomUUID(),ORG=UUID.randomUUID(),FOREIGN_ORG=UUID.randomUUID();
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private UUID country;
    @BeforeEach void references() {
        country=UUID.randomUUID();
        jdbc.update("insert into shared.country(id,iso2,iso3,name) values (?,'ZZ','ZZZ','Test country') on conflict (iso2) do update set name=excluded.name",country);
        country=jdbc.queryForObject("select id from shared.country where iso2='ZZ'",UUID.class);
        jdbc.update("insert into organization.organization(id,organization_number,name) values (?,?,?) on conflict do nothing",ORG,"ONBOARD-"+ORG,"Hospital A");
        jdbc.update("insert into organization.organization(id,organization_number,name) values (?,?,?) on conflict do nothing",FOREIGN_ORG,"ONBOARD-"+FOREIGN_ORG,"Hospital B");
    }
    @AfterEach void cleanup() {
        jdbc.update("delete from professional.organization_invitation where organization_id in (?,?)",ORG,FOREIGN_ORG);
        jdbc.update("delete from professional.registration_request where keycloak_user_id in (?,?)",APPLICANT,OTHER);
        jdbc.update("delete from professional.professional_assignment where professional_id in (select id from professional.professional where person_id in(select id from identity.person where keycloak_user_id in (?,?)))",APPLICANT,OTHER);
        jdbc.update("delete from professional.professional_license where professional_id in (select id from professional.professional where person_id in(select id from identity.person where keycloak_user_id in (?,?)))",APPLICANT,OTHER);
        jdbc.update("delete from professional.professional where person_id in(select id from identity.person where keycloak_user_id in (?,?))",APPLICANT,OTHER);
        jdbc.update("delete from professional.role_sync_outbox where subject_id in (?,?)",APPLICANT,OTHER);
        jdbc.update("delete from identity.person_contact where person_id in(select id from identity.person where keycloak_user_id in (?,?))",APPLICANT,OTHER);
        jdbc.update("delete from identity.person where keycloak_user_id in (?,?)",APPLICANT,OTHER);
        jdbc.update("delete from organization.organization where id in (?,?)",ORG,FOREIGN_ORG);
    }
    @Test void approvalIsIdempotentPrivateAndIndependentAndSuspensionRevokesRole() throws Exception  {
        UUID id=draft();
        mvc.perform(post("/api/v1/professional-onboarding/me/submit").header(HttpHeaders.AUTHORIZATION,"Bearer applicant")).andExpect(status().isConflict());
        mvc.perform(multipart("/api/v1/professional-onboarding/me/proof").file(new MockMultipartFile("file","license.pdf","application/pdf","%PDF-1.7 proof".getBytes())).header(HttpHeaders.AUTHORIZATION,"Bearer applicant")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/professional-onboarding/requests/{id}/proof",id).header(HttpHeaders.AUTHORIZATION,"Bearer other")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/professional-onboarding/me/submit").header(HttpHeaders.AUTHORIZATION,"Bearer applicant")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUBMITTED"));
        review(id,"APPROVE","APPROVED");
        review(id,"APPROVE","APPROVED");
        assertThat(jdbc.queryForObject("select count(*) from professional.professional p join identity.person person on person.id=p.person_id where person.keycloak_user_id=?",Integer.class,APPLICANT)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from professional.professional_assignment a join professional.professional p on p.id=a.professional_id join identity.person person on person.id=p.person_id where person.keycloak_user_id=?",Integer.class,APPLICANT)).isZero();
        assertThat(jdbc.queryForObject("select enabled from professional.role_sync_outbox where subject_id=?",Boolean.class,APPLICANT)).isTrue();
        review(id,"SUSPEND","SUSPENDED");
        assertThat(jdbc.queryForObject("select enabled from professional.role_sync_outbox where subject_id=?",Boolean.class,APPLICANT)).isFalse();
    }
    @Test void applicantCannotSelfApproveOrUploadExecutable() throws Exception  {
        mvc.perform(get("/api/v1/professional-onboarding/me")
                        .header(HttpHeaders.AUTHORIZATION,"Bearer applicant"))
                .andExpect(status().isOk()).andExpect(content().string("null"));
        UUID id=draft();
        mvc.perform(post("/api/v1/professional-onboarding/requests/{id}/review",id).header(HttpHeaders.AUTHORIZATION,"Bearer applicant").contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"APPROVE\",\"reason\":\"self approval\"}")).andExpect(status().isForbidden());
        mvc.perform(multipart("/api/v1/professional-onboarding/me/proof").file(new MockMultipartFile("file","fake.pdf","application/pdf","<script>bad</script>".getBytes())).header(HttpHeaders.AUTHORIZATION,"Bearer applicant"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PROOF_TYPE"));
        mvc.perform(multipart("/api/v1/professional-onboarding/me/proof")
                        .file(new MockMultipartFile("file","empty.pdf","application/pdf",new byte[0]))
                        .header(HttpHeaders.AUTHORIZATION,"Bearer applicant"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PROOF_SIZE"));
        mvc.perform(multipart("/api/v1/professional-onboarding/me/proof")
                        .file(new MockMultipartFile("file","oversized.pdf","application/pdf",new byte[5242881]))
                        .header(HttpHeaders.AUTHORIZATION,"Bearer applicant"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PROOF_SIZE"));
        mvc.perform(get("/api/v1/professional-onboarding/me")
                        .header(HttpHeaders.AUTHORIZATION,"Bearer applicant"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.proofUploaded").value(false));
    }
    @Test void organizationCannotInviteIntoAnotherTenantOrCreateProfessionalDirectly() throws Exception  {
        mvc.perform(post("/api/v1/professional-onboarding/invitations").header(HttpHeaders.AUTHORIZATION,"Bearer hospital").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"applicant@example.test\",\"organizationId\":\"%s\"}".formatted(FOREIGN_ORG))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/professionals").header(HttpHeaders.AUTHORIZATION,"Bearer hospital").contentType(MediaType.APPLICATION_JSON).content("{\"personId\":\"%s\",\"professionalNumber\":\"BYPASS\",\"professionalType\":\"DOCTOR\"}".formatted(APPLICANT))).andExpect(status().isForbidden());
    }
    @Test void invitationRequiresVerifiedIdentityApprovalAndScopedIdempotentAffiliation() throws Exception {
        UUID application=draft();
        String invitation=createInvitation();
        String token=extractString(invitation,"token");
        String acceptance="{\"token\":\"%s\"}".formatted(token);

        mvc.perform(post("/api/v1/professional-onboarding/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION,"Bearer other")
                        .contentType(MediaType.APPLICATION_JSON).content(acceptance))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/professional-onboarding/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION,"Bearer unverified")
                        .contentType(MediaType.APPLICATION_JSON).content(acceptance))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/professional-onboarding/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION,"Bearer applicant")
                        .contentType(MediaType.APPLICATION_JSON).content(acceptance))
                .andExpect(status().isConflict());

        mvc.perform(multipart("/api/v1/professional-onboarding/me/proof")
                        .file(new MockMultipartFile("file","license.pdf","application/pdf","%PDF-1.7 proof".getBytes()))
                        .header(HttpHeaders.AUTHORIZATION,"Bearer applicant"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/professional-onboarding/me/submit")
                        .header(HttpHeaders.AUTHORIZATION,"Bearer applicant"))
                .andExpect(status().isOk());
        review(application,"APPROVE","APPROVED");

        String affiliation=mvc.perform(post("/api/v1/professional-onboarding/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION,"Bearer applicant")
                        .contentType(MediaType.APPLICATION_JSON).content(acceptance))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.organizationId").value(ORG.toString()))
                .andReturn().getResponse().getContentAsString();
        UUID assignment=extractId(affiliation);
        mvc.perform(post("/api/v1/professional-onboarding/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION,"Bearer applicant")
                        .contentType(MediaType.APPLICATION_JSON).content(acceptance))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(assignment.toString()));
        assertThat(jdbc.queryForObject("select count(*) from professional.professional_assignment where organization_id=?",Integer.class,ORG))
                .isEqualTo(1);
        mvc.perform(get("/api/v1/professional-onboarding/me/affiliations")
                        .header(HttpHeaders.AUTHORIZATION,"Bearer applicant"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].organizationName").value("Hospital A"));
        mvc.perform(get("/api/v1/professional-onboarding/affiliations")
                        .header(HttpHeaders.AUTHORIZATION,"Bearer hospital-other"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        String expiredInvitation=createInvitation();
        UUID expiredId=extractId(expiredInvitation);
        jdbc.update("update professional.organization_invitation set expires_at=now()-interval '1 minute' where id=?",expiredId);
        mvc.perform(post("/api/v1/professional-onboarding/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION,"Bearer applicant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(extractString(expiredInvitation,"token"))))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/professional-onboarding/affiliations/{id}",assignment)
                        .header(HttpHeaders.AUTHORIZATION,"Bearer hospital-other"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select status from professional.professional_assignment where id=?",String.class,assignment))
                .isEqualTo("ACTIVE");
        mvc.perform(delete("/api/v1/professional-onboarding/affiliations/{id}",assignment)
                        .header(HttpHeaders.AUTHORIZATION,"Bearer hospital"))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select status from professional.professional_assignment where id=?",String.class,assignment))
                .isEqualTo("ENDED");
        mvc.perform(post("/api/v1/professional-onboarding/invitations/accept")
                        .header(HttpHeaders.AUTHORIZATION,"Bearer applicant")
                        .contentType(MediaType.APPLICATION_JSON).content(acceptance))
                .andExpect(status().isConflict());
    }

    private String createInvitation() throws Exception {
        return mvc.perform(post("/api/v1/professional-onboarding/invitations")
                        .header(HttpHeaders.AUTHORIZATION,"Bearer hospital")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"applicant@example.test\",\"organizationId\":\"%s\",\"position\":\"Doctor\"}".formatted(ORG)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
    }

    private String extractString(String body,String field) {
        var match=java.util.regex.Pattern.compile("\""+field+"\":\"([^\"]+)\"").matcher(body);
        if(!match.find())throw new IllegalArgumentException(body);
        return match.group(1);
    }

    private UUID draft() throws Exception  {
        String body=mvc.perform(put("/api/v1/professional-onboarding/me").header(HttpHeaders.AUTHORIZATION,"Bearer applicant").contentType(MediaType.APPLICATION_JSON).content("{\"profession\":\"medecin\",\"licenseNumber\":\"LIC-%s\",\"issuingAuthority\":\"College\",\"countryId\":\"%s\"}".formatted(APPLICANT,country))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT")).andReturn().getResponse().getContentAsString();
        return extractId(body);
    }
    private void review(UUID id,String decision,String status) throws Exception  {
        mvc.perform(post("/api/v1/professional-onboarding/requests/{id}/review",id).header(HttpHeaders.AUTHORIZATION,"Bearer admin").contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"%s\",\"reason\":\"Registry checked\"}".formatted(decision))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(status));
    }
    private UUID extractId(String body) {
        var match=java.util.regex.Pattern.compile("\"id\":\"([^\"]+)\"").matcher(body);
        if(!match.find())throw new IllegalArgumentException(body);
        return UUID.fromString(match.group(1));
    }
    @TestConfiguration(proxyBeanMethods=false) static class JwtFixtures  {
        @Bean JwtDecoder jwtDecoder() {
            return token-> {
                Instant now=Instant.now();
                UUID subject=Set.of("applicant","unverified").contains(token)?APPLICANT:"other".equals(token)?OTHER:UUID.randomUUID();
                String role="admin".equals(token)?"admin":token.startsWith("hospital")?"hopital":"patient";
                var builder=Jwt.withTokenValue(token).header("alg","RS256").subject(subject.toString()).issuer("https://keycloak.example/realms/healthys").audience(List.of("healthys-backend-apps")).issuedAt(now).expiresAt(now.plusSeconds(300)).claim("realm_access",Map.of("roles",List.of(role))).claim("given_name","Ada").claim("family_name","Lovelace").claim("email",Set.of("applicant","unverified").contains(token)?"applicant@example.test":"other@example.test").claim("email_verified",!"unverified".equals(token));
                if(token.startsWith("hospital"))builder.claim("healthys_organization_id",("hospital-other".equals(token)?FOREIGN_ORG:ORG).toString());
                return builder.build();
            }
            ;
        }
    }
}
