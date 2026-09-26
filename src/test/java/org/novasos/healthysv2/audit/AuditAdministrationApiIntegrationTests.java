package org.novasos.healthysv2.audit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.*;import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test") @SpringBootTest(properties={"spring.security.oauth2.resourceserver.jwt.issuer-uri=https://keycloak.example/realms/healthys","spring.security.oauth2.resourceserver.jwt.audiences=healthys-backend-apps","healthys.security.api-client-id=healthys-backend-apps","healthys.security.cors.allowed-origins=http://localhost:5173"}) @AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class,AuditAdministrationApiIntegrationTests.JwtFixtures.class}) @Transactional
class AuditAdministrationApiIntegrationTests {
    static final UUID ADMIN=UUID.fromString("72000000-0000-0000-0000-000000000001");@Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;

    @Test void searchesAuditAndDataAccessLogsAndBuildsDashboard()throws Exception{UUID patient=UUID.randomUUID(),event=UUID.randomUUID();jdbc.update("insert into audit.audit_log(module,entity_type,entity_id,action,new_value,correlation_id) values ('PATIENT','Patient',?,'UPDATE',?::jsonb,'corr-audit')",patient,"{\"status\":\"ACTIVE\"}");jdbc.update("insert into audit.data_access_log(patient_id,resource_type,resource_id,action,access_reason,access_context,correlation_id) values (?,'MEDICAL_RECORD',?,'DENIED','CONSENT_MISSING',?::jsonb,'corr-access')",patient,patient,"{\"allowed\":false}");jdbc.update("insert into audit.authentication_log(event_type,success,details) values ('LOGIN_ERROR',false,?::jsonb)","{\"realm\":\"healthys\"}");jdbc.update("insert into audit.security_event(id,event_type,severity,description,details) values (?,'SUSPICIOUS_ACCESS','HIGH','Repeated denied access',?::jsonb)",event,"{\"patientId\":\""+patient+"\"}");
        mvc.perform(get("/api/v1/admin/audit-logs").header(HttpHeaders.AUTHORIZATION,"Bearer admin").param("module","patient")).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].correlationId").value("corr-audit"));
        mvc.perform(get("/api/v1/admin/data-access-logs").header(HttpHeaders.AUTHORIZATION,"Bearer admin").param("action","denied")).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].accessReason").value("CONSENT_MISSING"));
        mvc.perform(get("/api/v1/admin/authentication-logs").header(HttpHeaders.AUTHORIZATION,"Bearer admin").param("success","false")).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].eventType").value("LOGIN_ERROR"));
        mvc.perform(get("/api/v1/admin/audit-dashboard").header(HttpHeaders.AUTHORIZATION,"Bearer admin")).andExpect(status().isOk()).andExpect(jsonPath("$.deniedAccesses").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1))).andExpect(jsonPath("$.authenticationFailures").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1))).andExpect(jsonPath("$.daily.length()").value(7));
        mvc.perform(post("/api/v1/admin/security-events/{id}/resolve",event).header(HttpHeaders.AUTHORIZATION,"Bearer admin")).andExpect(status().isOk()).andExpect(jsonPath("$.resolvedAt").exists());
    }

    @Test void exposesPlatformOverviewAndRejectsNonAdmin()throws Exception{mvc.perform(get("/api/v1/admin/overview").header(HttpHeaders.AUTHORIZATION,"Bearer admin")).andExpect(status().isOk()).andExpect(jsonPath("$.organizations").isNumber()).andExpect(jsonPath("$.auditEventsToday").isNumber());mvc.perform(get("/api/v1/admin/audit-logs").header(HttpHeaders.AUTHORIZATION,"Bearer user")).andExpect(status().isForbidden());}

    @TestConfiguration(proxyBeanMethods=false) static class JwtFixtures{@Bean JwtDecoder jwtDecoder(){return token->{Instant now=Instant.now();return Jwt.withTokenValue(token).header("alg","RS256").subject(ADMIN.toString()).issuer("https://keycloak.example/realms/healthys").audience(List.of("healthys-backend-apps")).issuedAt(now).expiresAt(now.plusSeconds(300)).claim("realm_access",Map.of("roles",List.of(token.equals("admin")?"PLATFORM_ADMIN":"PATIENT"))).claim("resource_access",Map.of()).build();};}}
}
