package org.novasos.healthysv2.appointment;

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

@SpringJUnitConfig(PatientAppointmentSecurityTests.Config.class)
@WebAppConfiguration
class PatientAppointmentSecurityTests {
    @Autowired WebApplicationContext context;
    @Autowired PatientAppointmentService service;
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
        mvc.perform(get("/api/v1/patients/me/appointments")
                .param("personId", UUID.randomUUID().toString())
                .param("patientId", UUID.randomUUID().toString())
                .header("Authorization", "Bearer patient")).andExpect(status().isOk());
        verify(service).list(subject, "upcoming", 0, 20);
        verifyNoMoreInteractions(service);
    }

    @Test void nonPatientRoleCannotReadPatientMedicalRecord() throws Exception {
        mvc.perform(get("/api/v1/patients/me/appointments").header("Authorization", "Bearer doctor"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void anonymousAndExpiredTokenCannotReadMedicalRecord() throws Exception {
        mvc.perform(get("/api/v1/patients/me/appointments")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/patients/me/appointments").header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void otherRolesCannotCreateCancelOrReschedule() throws Exception {
        String id = UUID.randomUUID().toString();
        String body = "{\"organizationId\":\"%s\",\"professionalId\":\"%s\",\"scheduledStart\":\"2030-01-01T10:00:00Z\",\"scheduledEnd\":\"2030-01-01T10:30:00Z\"}".formatted(id,id);
        for (String path : java.util.List.of("/api/v1/patients/me/appointments", "/api/v1/patients/me/appointments/"+id+"/cancel", "/api/v1/patients/me/appointments/"+id+"/reschedule")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(path)
                    .header("Authorization", "Bearer doctor")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    private Jwt token(String role) {
        return Jwt.withTokenValue("token").header("alg", "RS256").subject(subject.toString())
                .claim("role", role).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }

    @Configuration @EnableWebMvc @EnableMethodSecurity
    @org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    static class Config {
        @Bean PatientAppointmentService service() { return mock(PatientAppointmentService.class); }
        @Bean PatientAppointmentController controller(PatientAppointmentService service) { return new PatientAppointmentController(service); }
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
