package org.novasos.healthysv2.teleconsultation;

import static org.assertj.core.api.Assertions.*;import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;import com.fasterxml.jackson.databind.*;import java.time.*;import java.util.*;import org.junit.jupiter.api.Test;import org.novasos.healthysv2.TestcontainersConfiguration;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.*;import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;import org.springframework.context.annotation.*;import org.springframework.http.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.security.oauth2.jwt.*;import org.springframework.test.context.ActiveProfiles;import org.springframework.test.web.servlet.MockMvc;import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")@SpringBootTest(properties={"spring.security.oauth2.resourceserver.jwt.issuer-uri=https://keycloak.example/realms/healthys","spring.security.oauth2.resourceserver.jwt.audiences=healthys-backend-apps","healthys.security.api-client-id=healthys-backend-apps","healthys.security.cors.allowed-origins=http://localhost:5173"})@AutoConfigureMockMvc@Import({TestcontainersConfiguration.class,TeleconsultationApiIntegrationTests.JwtFixtures.class})@Transactional
class TeleconsultationApiIntegrationTests {
    @Autowired LiveKitRoomGateway rooms;@Autowired LiveKitRoomCleanup cleanup;
    @org.junit.jupiter.api.BeforeEach void resetProvider(){org.mockito.Mockito.reset(rooms);org.mockito.Mockito.when(rooms.delete(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);}
    static final UUID DOCTOR_SUBJECT=UUID.fromString("51000000-0000-0000-0000-000000000001"),PATIENT_SUBJECT=UUID.fromString("51000000-0000-0000-0000-000000000002");@Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;final ObjectMapper json=new ObjectMapper().findAndRegisterModules();
    @Test void runsWaitingRoomAndLiveKitTokenWorkflow()throws Exception{Graph g=graph();String created=mvc.perform(post("/api/v1/video-sessions").header(HttpHeaders.AUTHORIZATION,"Bearer doctor").contentType(MediaType.APPLICATION_JSON).content("{\"appointmentId\":\"%s\"}".formatted(g.appointment))).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SCHEDULED")).andExpect(jsonPath("$.participants.length()").value(2)).andReturn().getResponse().getContentAsString();UUID session=UUID.fromString(json.readTree(created).path("id").asText());String entered=mvc.perform(post("/api/v1/video-sessions/{id}/waiting-room",session).header(HttpHeaders.AUTHORIZATION,"Bearer patient")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WAITING")).andReturn().getResponse().getContentAsString();UUID entry=UUID.fromString(json.readTree(entered).path("id").asText());mvc.perform(post("/api/v1/video-sessions/{id}/token",session).header(HttpHeaders.AUTHORIZATION,"Bearer patient")).andExpect(status().isForbidden());mvc.perform(post("/api/v1/video-sessions/{id}/waiting-room/{entry}/admit",session,entry).header(HttpHeaders.AUTHORIZATION,"Bearer doctor")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ADMITTED"));mvc.perform(post("/api/v1/video-sessions/{id}/start",session).header(HttpHeaders.AUTHORIZATION,"Bearer doctor")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));String token=mvc.perform(post("/api/v1/video-sessions/{id}/token",session).header(HttpHeaders.AUTHORIZATION,"Bearer patient")).andExpect(status().isOk()).andExpect(jsonPath("$.serverUrl").value("ws://localhost:7880")).andReturn().getResponse().getContentAsString();assertThat(json.readTree(token).path("token").asText().split("\\.")).hasSize(3);mvc.perform(post("/api/v1/video-sessions/{id}/complete",session).header(HttpHeaders.AUTHORIZATION,"Bearer doctor")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));assertThat(jdbc.queryForObject("select count(*) from audit.audit_log where module='TELECONSULTATION' and entity_id=?",Integer.class,session)).isGreaterThanOrEqualTo(5);}
    @Test void databaseRejectsDuplicateParticipant(){Graph g=graph();UUID session=UUID.randomUUID();jdbc.update("insert into teleconsultation.video_session(id,session_number,appointment_id,provider,external_room_id) values (?,?,?,'LIVEKIT',?)",session,"TEL-DB",g.appointment,"healthys-db");jdbc.update("insert into teleconsultation.video_participant(video_session_id,person_id,role) values (?,?, 'PATIENT')",session,g.patientPerson);assertThatThrownBy(()->jdbc.update("insert into teleconsultation.video_participant(video_session_id,person_id,role) values (?,?, 'PATIENT')",session,g.patientPerson)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);}
    @Test void mobilePageAndDetailsAreStrictlyPatientScoped()throws Exception {
        Graph g=graph();UUID session=create(g);
        mvc.perform(get("/api/v1/video-sessions/page").header(HttpHeaders.AUTHORIZATION,"Bearer patient").param("size","1")).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.page.totalElements").value(1)).andExpect(jsonPath("$.content[0].participants[0].displayName").value("Video User"));
        person(UUID.fromString("51000000-0000-0000-0000-000000000003"));
        mvc.perform(get("/api/v1/video-sessions/page").header(HttpHeaders.AUTHORIZATION,"Bearer other")).andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(0));
        for(String action:List.of("","/waiting-room","/token","/leave")){
            var request=action.isEmpty()?get("/api/v1/video-sessions/{id}",session):post("/api/v1/video-sessions/{id}"+action,session);
            mvc.perform(request.header(HttpHeaders.AUTHORIZATION,"Bearer other")).andExpect(status().isForbidden());
        }
    }
    @Test void rejectsPersonUuidImpersonationAndExpiredJwt()throws Exception {
        Graph g=graph();UUID session=create(g);
        mvc.perform(get("/api/v1/video-sessions/{id}",session).header(HttpHeaders.AUTHORIZATION,"Bearer spoof:"+g.patientPerson)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/video-sessions/{id}",session).header(HttpHeaders.AUTHORIZATION,"Bearer expired")).andExpect(status().isForbidden());
    }
    @Test void closedSessionsCannotReenterWaitingRoomOrMintTokens()throws Exception {
        Graph g=graph();UUID session=create(g);
        for(String state:List.of("CANCELLED","COMPLETED")){
            jdbc.update("update teleconsultation.video_session set status=? where id=?",state,session);
            mvc.perform(post("/api/v1/video-sessions/{id}/waiting-room",session).header(HttpHeaders.AUTHORIZATION,"Bearer patient")).andExpect(status().isUnprocessableEntity());
            mvc.perform(post("/api/v1/video-sessions/{id}/token",session).header(HttpHeaders.AUTHORIZATION,"Bearer doctor")).andExpect(status().isUnprocessableEntity());
        }
        org.mockito.Mockito.verifyNoInteractions(rooms);
    }
    @Test void repeatedWaitingEntryPreservesAdmissionAndPatientLeaveDoesNotEndRoom()throws Exception {
        UUID session=create(graph());UUID entry=enter(session);admit(session,entry);
        mvc.perform(post("/api/v1/video-sessions/{id}/waiting-room",session).header(HttpHeaders.AUTHORIZATION,"Bearer patient")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ADMITTED"));
        mvc.perform(post("/api/v1/video-sessions/{id}/token",session).header(HttpHeaders.AUTHORIZATION,"Bearer patient")).andExpect(status().isUnprocessableEntity());
        start(session);
        mvc.perform(post("/api/v1/video-sessions/{id}/complete",session).header(HttpHeaders.AUTHORIZATION,"Bearer patient")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/video-sessions/{id}/leave",session).header(HttpHeaders.AUTHORIZATION,"Bearer patient")).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select status from teleconsultation.video_session where id=?",String.class,session)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select status from teleconsultation.waiting_room where id=?",String.class,entry)).isEqualTo("LEFT");
        mvc.perform(post("/api/v1/video-sessions/{id}/token",session).header(HttpHeaders.AUTHORIZATION,"Bearer patient")).andExpect(status().isForbidden());
    }
    @Test void onlyAuthorizedStartCreatesProviderRoomAndFailedProviderKeepsWaiting()throws Exception {
        UUID session=create(graph());
        mvc.perform(post("/api/v1/video-sessions/{id}/start",session).header(HttpHeaders.AUTHORIZATION,"Bearer patient")).andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(rooms);
        org.mockito.Mockito.doThrow(new IllegalStateException("Video provider unavailable")).when(rooms).create(org.mockito.ArgumentMatchers.anyString());
        mvc.perform(post("/api/v1/video-sessions/{id}/start",session).header(HttpHeaders.AUTHORIZATION,"Bearer doctor")).andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForObject("select status from teleconsultation.video_session where id=?",String.class,session)).isEqualTo("SCHEDULED");
    }
    @Test void completionPersistsDeletionAndRetriesProviderFailure()throws Exception {
        UUID session=create(graph());start(session);
        mvc.perform(post("/api/v1/video-sessions/{id}/complete",session).header(HttpHeaders.AUTHORIZATION,"Bearer doctor")).andExpect(status().isOk());
        org.mockito.Mockito.verify(rooms,org.mockito.Mockito.never()).delete(org.mockito.ArgumentMatchers.anyString());
        assertThat(jdbc.queryForObject("select count(*) from teleconsultation.room_cleanup where video_session_id=? and completed_at is null",Integer.class,session)).isEqualTo(1);
        org.mockito.Mockito.when(rooms.delete(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);cleanup.dispatch();
        assertThat(jdbc.queryForObject("select attempts from teleconsultation.room_cleanup where video_session_id=?",Integer.class,session)).isEqualTo(1);
        jdbc.update("update teleconsultation.room_cleanup set next_attempt_at=now() where video_session_id=?",session);
        org.mockito.Mockito.when(rooms.delete(org.mockito.ArgumentMatchers.anyString())).thenReturn(true);cleanup.dispatch();
        assertThat(jdbc.queryForObject("select completed_at is not null from teleconsultation.room_cleanup where video_session_id=?",Boolean.class,session)).isTrue();
    }
    @Test void cancelledAppointmentBlocksStartWaitingAndTokenEvenIfVideoStillScheduled()throws Exception {
        Graph graph=graph();UUID session=create(graph);
        jdbc.update("update appointment.appointment set status='CANCELLED' where id=?",graph.appointment);
        mvc.perform(post("/api/v1/video-sessions/{id}/waiting-room",session).header(HttpHeaders.AUTHORIZATION,"Bearer patient")).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/video-sessions/{id}/start",session).header(HttpHeaders.AUTHORIZATION,"Bearer doctor")).andExpect(status().isUnprocessableEntity());
        mvc.perform(post("/api/v1/video-sessions/{id}/token",session).header(HttpHeaders.AUTHORIZATION,"Bearer doctor")).andExpect(status().isUnprocessableEntity());
        org.mockito.Mockito.verifyNoInteractions(rooms);
    }
    @Test void exhaustedRoomDeletionStaysAuditableForOperatorRetry()throws Exception {
        UUID session=create(graph());start(session);
        mvc.perform(post("/api/v1/video-sessions/{id}/complete",session).header(HttpHeaders.AUTHORIZATION,"Bearer doctor")).andExpect(status().isOk());
        jdbc.update("update teleconsultation.room_cleanup set attempts=19 where video_session_id=?",session);
        org.mockito.Mockito.when(rooms.delete(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);cleanup.dispatch();
        assertThat(jdbc.queryForObject("select failed_at is not null and last_error='PROVIDER_UNAVAILABLE' from teleconsultation.room_cleanup where video_session_id=?",Boolean.class,session)).isTrue();
        org.mockito.Mockito.clearInvocations(rooms);cleanup.dispatch();org.mockito.Mockito.verifyNoInteractions(rooms);
    }
    private UUID create(Graph graph)throws Exception {return UUID.fromString(json.readTree(mvc.perform(post("/api/v1/video-sessions").header(HttpHeaders.AUTHORIZATION,"Bearer doctor").contentType(MediaType.APPLICATION_JSON).content("{\"appointmentId\":\"%s\"}".formatted(graph.appointment))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asText());}
    private UUID enter(UUID session)throws Exception {return UUID.fromString(json.readTree(mvc.perform(post("/api/v1/video-sessions/{id}/waiting-room",session).header(HttpHeaders.AUTHORIZATION,"Bearer patient")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("id").asText());}
    private void admit(UUID session,UUID entry)throws Exception {mvc.perform(post("/api/v1/video-sessions/{id}/waiting-room/{entry}/admit",session,entry).header(HttpHeaders.AUTHORIZATION,"Bearer doctor")).andExpect(status().isOk());}
    private void start(UUID session)throws Exception {mvc.perform(post("/api/v1/video-sessions/{id}/start",session).header(HttpHeaders.AUTHORIZATION,"Bearer doctor")).andExpect(status().isOk());}
    private Graph graph(){UUID doctor=person(DOCTOR_SUBJECT),patientPerson=person(PATIENT_SUBJECT),organization=UUID.randomUUID(),professional=UUID.randomUUID(),patient=UUID.randomUUID(),appointment=UUID.randomUUID();jdbc.update("insert into organization.organization(id,organization_number,name) values (?,?,?)",organization,"ORG-"+organization,"Hospital");jdbc.update("insert into professional.professional(id,person_id,professional_number,professional_type,status) values (?,?,?,?, 'ACTIVE')",professional,doctor,"PRO-"+professional,"DOCTOR");jdbc.update("insert into patient.patient(id,person_id,patient_number) values (?,?,?)",patient,patientPerson,"PAT-"+patient);jdbc.update("insert into appointment.appointment(id,appointment_number,patient_id,professional_id,organization_id,type,scheduled_start,scheduled_end,status) values (?,?,?,?,?,'TELECONSULTATION',?,?, 'SCHEDULED')",appointment,"APT-"+appointment,patient,professional,organization,java.sql.Timestamp.from(Instant.now().plusSeconds(3600)),java.sql.Timestamp.from(Instant.now().plusSeconds(5400)));return new Graph(appointment,patientPerson);}private UUID person(UUID subject){UUID id=UUID.randomUUID();jdbc.update("insert into identity.person(id,person_number,keycloak_user_id,first_name,last_name,status) values (?,?,?,?,?,'ACTIVE')",id,"PER-"+id,subject,"Video","User");return id;}record Graph(UUID appointment,UUID patientPerson){}
    @TestConfiguration(proxyBeanMethods=false)static class JwtFixtures{@Bean @Primary LiveKitRoomGateway roomGateway(){return org.mockito.Mockito.mock(LiveKitRoomGateway.class);}@Bean JwtDecoder jwtDecoder(){return token->{Instant now=Instant.now();boolean patient="patient".equals(token)||"other".equals(token)||token.startsWith("spoof:")||"expired".equals(token);String subject=token.startsWith("spoof:")?token.substring(6):"other".equals(token)?"51000000-0000-0000-0000-000000000003":(patient?PATIENT_SUBJECT:DOCTOR_SUBJECT).toString();return Jwt.withTokenValue(token).header("alg","RS256").subject(subject).issuer("https://keycloak.example/realms/healthys").audience(List.of("healthys-backend-apps")).issuedAt(now.minusSeconds(60)).expiresAt("expired".equals(token)?now.minusSeconds(1):now.plusSeconds(300)).claim("realm_access",Map.of("roles",List.of(patient?"PATIENT":"DOCTOR"))).claim("resource_access",Map.of()).build();};}}
}
