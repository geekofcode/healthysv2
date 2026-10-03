package org.novasos.healthysv2.patient;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

@SpringJUnitConfig(PatientDashboardSecurityTests.Config.class)
@WebAppConfiguration
class PatientDashboardSecurityTests {
    @Autowired WebApplicationContext context;
    @Autowired PatientDashboardService service;
    @Autowired JwtDecoder decoder;
    private MockMvc mvc;
    private final UUID subject = UUID.randomUUID();

    @BeforeEach
    void setup() {
        reset(service, decoder);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(decoder.decode("patient")).thenReturn(token("PATIENT"));
        when(decoder.decode("doctor")).thenReturn(token("DOCTOR"));
        when(decoder.decode("expired")).thenAnswer(invocation -> {
            var expired = Jwt.withTokenValue("expired").header("alg", "RS256")
                    .subject(subject.toString()).claim("role", "PATIENT")
                    .issuedAt(Instant.now().minusSeconds(3600))
                    .expiresAt(Instant.now().minusSeconds(300)).build();
            var validation = new JwtTimestampValidator().validate(expired);
            if (validation.hasErrors()) {
                throw new JwtValidationException("Expired JWT", validation.getErrors());
            }
            return expired;
        });
    }

    @Test void authorizedPatientUsesOnlyVerifiedSubjectEvenWithSpoofedParameters() throws Exception {
        mvc.perform(get("/api/v1/patients/me/dashboard")
                .param("personId", UUID.randomUUID().toString())
                .param("patientId", UUID.randomUUID().toString())
                .header("Authorization", "Bearer patient")).andExpect(status().isOk());
        verify(service).findMe(subject);
        verifyNoMoreInteractions(service);
    }

    @Test void nonPatientRoleCannotReadPatientDashboard() throws Exception {
        mvc.perform(get("/api/v1/patients/me/dashboard").header("Authorization", "Bearer doctor"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void anonymousAndExpiredTokenCannotReadDashboard() throws Exception {
        mvc.perform(get("/api/v1/patients/me/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/patients/me/dashboard").header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    private Jwt token(String role) {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject(subject.toString())
                .claim("role", role).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    @Configuration @EnableWebMvc @EnableMethodSecurity
    @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    static class Config {
        @Bean PatientDashboardService service() { return mock(PatientDashboardService.class); }
        @Bean PatientDashboardController controller(PatientDashboardService service) { return new PatientDashboardController(service); }
        @Bean JwtDecoder decoder() { return mock(JwtDecoder.class); }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            var converter = new JwtAuthenticationConverter();
            converter.setJwtGrantedAuthoritiesConverter(jwt -> java.util.List.of(
                    new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role"))));
            return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))).build();
        }
    }
}
