package org.novasos.healthysv2.messaging;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.io.*;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

@ActiveProfiles("test")
@SpringBootTest(properties={"spring.security.oauth2.resourceserver.jwt.issuer-uri=https://keycloak.example/realms/healthys","healthys.security.api-client-id=healthys-backend-apps","healthys.security.cors.allowed-origins=http://localhost:5173"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class,MobileMessagingApiIntegrationTests.Fixtures.class})
@Transactional
class MobileMessagingApiIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json=new ObjectMapper().findAndRegisterModules();

    @Test void patientDirectoryAndCreationUseOnlyActiveCareRelationships()throws Exception{
        Graph graph=graph();
        mvc.perform(get("/api/v1/conversations/recipients").header(HttpHeaders.AUTHORIZATION,"Bearer patient"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].personId").value(graph.doctor.toString()));
        mvc.perform(post("/api/v1/conversations").header(HttpHeaders.AUTHORIZATION,"Bearer patient").contentType(MediaType.APPLICATION_JSON).content(create(graph.doctor)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/conversations").header(HttpHeaders.AUTHORIZATION,"Bearer patient").contentType(MediaType.APPLICATION_JSON).content(create(graph.outsider)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/conversations").header(HttpHeaders.AUTHORIZATION,"Bearer person-"+graph.person))
                .andExpect(status().isForbidden());
    }

    @Test void messageReceiptsReflectOtherReaderAndMembershipRevocation()throws Exception{
        Graph graph=graph();UUID conversation=conversation(graph);UUID message=send(conversation,"{\"type\":\"TEXT\",\"content\":\"Bonjour\"}");
        mvc.perform(get("/api/v1/conversations/{id}/messages",conversation).header(HttpHeaders.AUTHORIZATION,"Bearer patient"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].readByCurrentUser").value(true)).andExpect(jsonPath("$.content[0].readByOthersCount").value(0));
        mvc.perform(get("/api/v1/conversations").header(HttpHeaders.AUTHORIZATION,"Bearer doctor"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].unreadCount").value(1));
        mvc.perform(post("/api/v1/conversations/messages/{id}/read",message).header(HttpHeaders.AUTHORIZATION,"Bearer doctor")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/conversations/{id}/messages",conversation).header(HttpHeaders.AUTHORIZATION,"Bearer patient"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].readByOthersCount").value(1));
        jdbc.update("update communication.conversation_participant set left_at=now(),status='LEFT' where conversation_id=? and person_id=?",conversation,graph.doctor);
        mvc.perform(get("/api/v1/conversations/{id}/messages",conversation).header(HttpHeaders.AUTHORIZATION,"Bearer doctor")).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/conversations/messages/{id}/read",message).header(HttpHeaders.AUTHORIZATION,"Bearer outsider")).andExpect(status().isForbidden());
    }

    @Test void attachmentsRequireMembershipScopeAndExplicitSharing()throws Exception{
        Graph graph=graph();UUID conversation=conversation(graph);var file=new MockMultipartFile("file","note.txt","text/plain","stored".getBytes());
        UUID attachment=UUID.fromString(json.readTree(mvc.perform(multipart("/api/v1/conversations/{id}/attachments",conversation).file(file).header(HttpHeaders.AUTHORIZATION,"Bearer patient"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sizeBytes").value(6)).andReturn().getResponse().getContentAsString()).path("id").asText());
        mvc.perform(get("/api/v1/conversations/{c}/attachments/{a}",conversation,attachment).header(HttpHeaders.AUTHORIZATION,"Bearer doctor")).andExpect(status().isForbidden());
        send(conversation,"{\"type\":\"DOCUMENT\",\"documentIds\":[\""+attachment+"\"]}");
        mvc.perform(get("/api/v1/conversations/{c}/attachments/{a}/content",conversation,attachment).header(HttpHeaders.AUTHORIZATION,"Bearer doctor"))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL,"no-store")).andExpect(content().bytes("stored".getBytes()));
        mvc.perform(get("/api/v1/conversations/{c}/attachments/{a}",conversation,attachment).header(HttpHeaders.AUTHORIZATION,"Bearer outsider")).andExpect(status().isForbidden());
        UUID second=conversation(graph);
        mvc.perform(post("/api/v1/conversations/{id}/messages",second).header(HttpHeaders.AUTHORIZATION,"Bearer patient").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"DOCUMENT\",\"documentIds\":[\""+attachment+"\"]}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select patient_visible from document.document where id=?",Boolean.class,attachment)).isFalse();
        assertThat(jdbc.queryForObject("select owner_patient_id from document.document where id=?",UUID.class,attachment)).isNull();
        mvc.perform(multipart("/api/v1/conversations/{id}/attachments",conversation).file(new MockMultipartFile("file","note.pdf","application/pdf","MZbad".getBytes())).header(HttpHeaders.AUTHORIZATION,"Bearer patient"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test void patientCannotSendUnpublishedOrOtherPatientDocuments()throws Exception{
        Graph graph=graph();UUID conversation=conversation(graph),document=UUID.randomUUID();
        jdbc.update("insert into document.document(id,document_number,owner_patient_id,file_name,storage_key,storage_provider,mime_type,size_bytes,uploaded_by) values (?,?,?,'private.txt',?,'MINIO','text/plain',6,?)",document,"DOC-"+document,graph.patient,document.toString(),graph.doctor);
        mvc.perform(post("/api/v1/conversations/{id}/messages",conversation).header(HttpHeaders.AUTHORIZATION,"Bearer patient").contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"DOCUMENT\",\"documentIds\":[\""+document+"\"]}"))
                .andExpect(status().isForbidden());
        jdbc.update("update document.document set patient_visible=true where id=?",document);
        send(conversation,"{\"type\":\"DOCUMENT\",\"documentIds\":[\""+document+"\"]}");
        mvc.perform(get("/api/v1/conversations/{id}/messages",conversation).param("size","101").header(HttpHeaders.AUTHORIZATION,"Bearer patient")).andExpect(status().isOk()).andExpect(jsonPath("$.page.size").value(100));
    }

    private String create(UUID recipient){return "{\"type\":\"DIRECT\",\"participantPersonIds\":[\""+recipient+"\"]}";}
    private UUID conversation(Graph graph)throws Exception{return UUID.fromString(json.readTree(mvc.perform(post("/api/v1/conversations").header(HttpHeaders.AUTHORIZATION,"Bearer patient").contentType(MediaType.APPLICATION_JSON).content(create(graph.doctor))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asText());}
    private UUID send(UUID conversation,String body)throws Exception{return UUID.fromString(json.readTree(mvc.perform(post("/api/v1/conversations/{id}/messages",conversation).header(HttpHeaders.AUTHORIZATION,"Bearer patient").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).path("id").asText());}
    private Graph graph(){
        UUID person=person("patient"),doctor=person("doctor"),outsider=person("outsider"),patient=UUID.randomUUID(),professional=UUID.randomUUID(),organization=UUID.randomUUID();
        jdbc.update("insert into patient.patient(id,person_id,patient_number) values (?,?,?)",patient,person,"PAT-"+patient);
        jdbc.update("insert into professional.professional(id,person_id,professional_number,professional_type) values (?,?,?,'DOCTOR')",professional,doctor,"PRO-"+professional);
        jdbc.update("insert into organization.organization(id,organization_number,name) values (?,?,'Clinic')",organization,"ORG-"+organization);
        jdbc.update("insert into patient.care_relationship(patient_id,professional_id,organization_id,relationship_type) values (?,?,?,'PRIMARY')",patient,professional,organization);
        return new Graph(person,patient,doctor,outsider);
    }
    private UUID person(String token){UUID id=UUID.randomUUID(),subject=UUID.randomUUID();jdbc.update("insert into identity.person(id,keycloak_user_id,person_number,first_name,last_name) values (?,?,?,?,?)",id,subject,"PER-"+id,token,"User");Fixtures.SUBJECTS.put(token,subject);return id;}
    record Graph(UUID person,UUID patient,UUID doctor,UUID outsider){}

    @TestConfiguration(proxyBeanMethods=false)
    static class Fixtures {
        static final Map<String,UUID> SUBJECTS=new HashMap<>();
        @Bean JwtDecoder jwtDecoder(){return token->{Instant now=Instant.now();UUID subject=token.startsWith("person-")?UUID.fromString(token.substring(7)):SUBJECTS.getOrDefault(token,UUID.randomUUID());return Jwt.withTokenValue(token).header("alg","RS256").subject(subject.toString()).issuer("https://keycloak.example/realms/healthys").issuedAt(now).expiresAt(now.plusSeconds(300)).claim("realm_access",Map.of("roles",List.of(token.equals("patient")?"PATIENT":"DOCTOR"))).claim("resource_access",Map.of()).build();};}
        @Bean @Primary S3Client messagingStorage(){var client=mock(S3Client.class);when(client.getObject(any(GetObjectRequest.class))).thenAnswer(invocation->new ResponseInputStream<>(GetObjectResponse.builder().contentLength(6L).contentType("text/plain").build(),AbortableInputStream.create(new ByteArrayInputStream("stored".getBytes()))));return client;}
    }
}
