package org.novasos.healthysv2.hospital;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;

@ActiveProfiles("test")
@SpringBootTest(properties={"spring.security.oauth2.resourceserver.jwt.issuer-uri=https://keycloak.example/realms/healthys","spring.security.oauth2.resourceserver.jwt.audiences=healthys-api","healthys.security.api-client-id=healthys-api","healthys.security.cors.allowed-origins=http://localhost:5173"})
@AutoConfigureMockMvc @Import({TestcontainersConfiguration.class,OrganizationApiIntegrationTests.JwtFixtures.class}) @Transactional
class OrganizationApiIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test void adminCreatesAndReadsAnOrganization() throws Exception {
        String location=mockMvc.perform(post("/api/v1/organizations").header(HttpHeaders.AUTHORIZATION,"Bearer admin").contentType(MediaType.APPLICATION_JSON).content("""
                {"number":"ORG-API","name":"General Hospital","status":"ACTIVE"}
                """)).andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("General Hospital")).andReturn().getResponse().getHeader("Location");
        mockMvc.perform(get(location).header(HttpHeaders.AUTHORIZATION,"Bearer agent"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.number").value("ORG-API"));
    }

    @Test void hierarchyRejectsDepartmentFromAnotherOrganization() throws Exception {
        UUID first=create("ORG-A"); UUID second=create("ORG-B"); UUID foreignDepartment=addDepartment(second,"ER");
        mockMvc.perform(post("/api/v1/organizations/{id}/rooms",first).header(HttpHeaders.AUTHORIZATION,"Bearer admin").contentType(MediaType.APPLICATION_JSON).content("""
                {"departmentId":"%s","roomNumber":"101"}
                """.formatted(foreignDepartment))).andExpect(status().isNotFound());
    }

    @Test void agentCannotModifyOrganizations() throws Exception {
        mockMvc.perform(post("/api/v1/organizations").header(HttpHeaders.AUTHORIZATION,"Bearer agent").contentType(MediaType.APPLICATION_JSON).content("""
                {"number":"ORG-FORBIDDEN","name":"Forbidden"}
                """)).andExpect(status().isForbidden());
    }

    @Test void apiErrorsFollowAcceptLanguage() throws Exception {
        mockMvc.perform(get("/api/v1/organizations/{id}",UUID.randomUUID()).header(HttpHeaders.AUTHORIZATION,"Bearer agent").header(HttpHeaders.ACCEPT_LANGUAGE,"fr"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("introuvable")));
    }

    private UUID create(String number) throws Exception {String body=mockMvc.perform(post("/api/v1/organizations").header(HttpHeaders.AUTHORIZATION,"Bearer admin").contentType(MediaType.APPLICATION_JSON).content("{\"number\":\""+number+"\",\"name\":\"Test\"}" )).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();return UUID.fromString(objectMapper.readTree(body).get("id").asText());}
    private UUID addDepartment(UUID org,String code) throws Exception {String body=mockMvc.perform(post("/api/v1/organizations/{id}/departments",org).header(HttpHeaders.AUTHORIZATION,"Bearer admin").contentType(MediaType.APPLICATION_JSON).content("{\"code\":\""+code+"\",\"name\":\"Department\"}" )).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();return UUID.fromString(objectMapper.readTree(body).get("id").asText());}

    @TestConfiguration(proxyBeanMethods=false) static class JwtFixtures {
        @Bean JwtDecoder jwtDecoder(){return token->jwt(token,token.equals("admin")?List.of("PLATFORM_ADMIN"):List.of("HOSPITAL_AGENT"));}
        private Jwt jwt(String token,List<String> roles){Instant now=Instant.now();return Jwt.withTokenValue(token).header("alg","RS256").subject(UUID.randomUUID().toString()).issuer("https://keycloak.example/realms/healthys").audience(List.of("healthys-api")).issuedAt(now).expiresAt(now.plusSeconds(300)).claim("realm_access",Map.of("roles",roles)).claim("resource_access",Map.of()).build();}
    }
}
