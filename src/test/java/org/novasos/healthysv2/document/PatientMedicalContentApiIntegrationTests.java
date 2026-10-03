package org.novasos.healthysv2.document;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest(properties={
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://keycloak.example/realms/healthys",
    "spring.security.oauth2.resourceserver.jwt.audiences=healthys-backend-apps",
    "healthys.security.api-client-id=healthys-backend-apps",
    "healthys.security.cors.allowed-origins=http://localhost:5173"
})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class,PatientMedicalContentApiIntegrationTests.Fixtures.class})
@Transactional
class PatientMedicalContentApiIntegrationTests {
    static final UUID SUBJECT=UUID.fromString("86000000-0000-0000-0000-000000000001");
    static final UUID OTHER=UUID.fromString("86000000-0000-0000-0000-000000000002");
    static final byte[] PDF="%PDF-1.4\n%%EOF".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test void onlyExplicitlyPublishedContentIsVisibleAndRevocationRemovesIt() throws Exception {
        Graph g=graph();
        mvc.perform(get("/api/v1/patients/me/consultations").header(HttpHeaders.AUTHORIZATION,"Bearer patient"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
        assertContent(g,0,0);
        assertDocuments(0);
        mvc.perform(get("/api/v1/patients/me/documents/{id}/content",g.document).header(HttpHeaders.AUTHORIZATION,"Bearer patient"))
            .andExpect(status().isNotFound());
        visibility("/api/v1/consultations/"+g.consultation+"/notes/"+g.note+"/patient-visibility",true);
        visibility("/api/v1/consultations/"+g.consultation+"/diagnoses/"+g.diagnosis+"/patient-visibility",true);
        visibility("/api/v1/documents/"+g.document+"/patient-visibility",true);
        assertContent(g,1,1);
        mvc.perform(get("/api/v1/patients/me/consultations/{id}",g.consultation).header(HttpHeaders.AUTHORIZATION,"Bearer patient"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.notes[0].content").value("Published patient summary"))
            .andExpect(jsonPath("$.diagnoses[0].description").value("Published diagnosis"))
            .andExpect(jsonPath("$.consultation.reason").doesNotExist())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Never publish"))));
        assertDocuments(1);
        mvc.perform(get("/api/v1/patients/me/documents").param("consultationId",g.consultation.toString()).header(HttpHeaders.AUTHORIZATION,"Bearer patient"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(g.document.toString()));
        mvc.perform(get("/api/v1/patients/me/documents/{id}/content",g.document).header(HttpHeaders.AUTHORIZATION,"Bearer patient"))
            .andExpect(status().isOk()).andExpect(content().bytes(PDF))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL,org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(header().string("X-Content-Type-Options","nosniff"));
        visibility("/api/v1/consultations/"+g.consultation+"/notes/"+g.note+"/patient-visibility",false);
        visibility("/api/v1/consultations/"+g.consultation+"/diagnoses/"+g.diagnosis+"/patient-visibility",false);
        visibility("/api/v1/documents/"+g.document+"/patient-visibility",false);
        assertContent(g,0,0);
        assertDocuments(0);
        mvc.perform(get("/api/v1/documents/{id}/content",g.document).header(HttpHeaders.AUTHORIZATION,"Bearer patient"))
            .andExpect(status().isNotFound());
    }
    @Test void anotherPatientCannotReadOrPublishTheContent() throws Exception {
        Graph g=graph();
        patient(OTHER);
        mvc.perform(get("/api/v1/patients/me/consultations/{id}",g.consultation).header(HttpHeaders.AUTHORIZATION,"Bearer other"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/patients/me/documents/{id}/content",g.document).header(HttpHeaders.AUTHORIZATION,"Bearer other"))
            .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/documents/{id}/patient-visibility",g.document).header(HttpHeaders.AUTHORIZATION,"Bearer patient")
            .contentType(MediaType.APPLICATION_JSON).content("{\"patientVisible\":true}"))
            .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/consultations/{id}/notes/{note}/patient-visibility",g.consultation,g.note).header(HttpHeaders.AUTHORIZATION,"Bearer patient")
            .contentType(MediaType.APPLICATION_JSON).content("{\"patientVisible\":true}"))
            .andExpect(status().isForbidden());
    }
    private void assertContent(Graph g,int notes,int diagnoses) throws Exception {
        mvc.perform(get("/api/v1/patients/me/consultations/{id}",g.consultation).header(HttpHeaders.AUTHORIZATION,"Bearer patient"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.notes.length()").value(notes)).andExpect(jsonPath("$.diagnoses.length()").value(diagnoses));
    }
    private void assertDocuments(int size) throws Exception {
        mvc.perform(get("/api/v1/patients/me/documents").header(HttpHeaders.AUTHORIZATION,"Bearer patient"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(size));
    }
    private void visibility(String path,boolean visible) throws Exception {
        mvc.perform(patch(path).header(HttpHeaders.AUTHORIZATION,"Bearer admin").contentType(MediaType.APPLICATION_JSON)
            .content("{\"patientVisible\":"+visible+"}")).andExpect(status().isOk());
    }
    private Graph graph() {
        UUID patient=patient(SUBJECT),doctorPerson=person(null),professional=UUID.randomUUID(),organization=UUID.randomUUID();
        UUID consultation=UUID.randomUUID(),note=UUID.randomUUID(),diagnosis=UUID.randomUUID(),document=UUID.randomUUID();
        jdbc.update("insert into professional.professional(id,person_id,professional_number,professional_type,status) values (?,?,?,?,?)",professional,doctorPerson,"PRO-"+professional,"DOCTOR","ACTIVE");
        jdbc.update("insert into organization.organization(id,organization_number,name) values (?,?,?)",organization,"ORG-"+organization,"Hospital");
        jdbc.update("insert into consultation.consultation(id,consultation_number,patient_id,professional_id,organization_id,type,reason,status,completed_at) values (?,?,?,?,?,?,?,?,now())",consultation,"CON-"+consultation,patient,professional,organization,"GENERAL","Never publish consultation reason","COMPLETED");
        jdbc.update("insert into consultation.consultation_note(id,consultation_id,author_id,note_type,content) values (?,?,?,?,?)",note,consultation,professional,"SUMMARY","Published patient summary");
        jdbc.update("insert into consultation.consultation_note(id,consultation_id,author_id,note_type,content) values (?,?,?,?,?)",UUID.randomUUID(),consultation,professional,"PRIVATE","Never publish private note");
        jdbc.update("insert into consultation.diagnosis(id,consultation_id,diagnosis_type,description) values (?,?,?,?)",diagnosis,consultation,"PRIMARY","Published diagnosis");
        jdbc.update("insert into document.document(id,document_number,owner_patient_id,file_name,storage_key,storage_provider,mime_type,size_bytes,status) values (?,?,?,?,?,?,?,?,?)",document,"DOC-"+document,patient,"report.pdf","test/"+document,"MINIO","application/pdf",PDF.length,"ACTIVE");
        jdbc.update("insert into document.document_link(document_id,resource_type,resource_id) values (?,?,?)",document,"CONSULTATION",consultation);
        return new Graph(consultation,note,diagnosis,document);
    }
    private UUID patient(UUID subject) {
        UUID person=person(subject),patient=UUID.randomUUID();
        jdbc.update("insert into patient.patient(id,person_id,patient_number) values (?,?,?)",patient,person,"PAT-"+patient);
        return patient;
    }
    private UUID person(UUID subject) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into identity.person(id,person_number,keycloak_user_id,first_name,last_name,status) values (?,?,?,?,?,?)",id,"PER-"+id,subject,"Ada","Patient","ACTIVE");
        return id;
    }
    private record Graph(UUID consultation,UUID note,UUID diagnosis,UUID document) {}
    @TestConfiguration(proxyBeanMethods=false) static class Fixtures {
        @Bean @Primary DocumentStorage documentStorage() {
            var storage=mock(DocumentStorage.class);
            when(storage.get(anyString())).thenAnswer(invocation -> new DocumentStorage.StoredObject(new ByteArrayInputStream(PDF),PDF.length,"application/pdf"));
            return storage;
        }
        @Bean JwtDecoder jwtDecoder() {
            return token -> {
                Instant now=Instant.now();
                return Jwt.withTokenValue(token).header("alg","RS256").subject((token.equals("other")?OTHER:SUBJECT).toString())
                    .issuer("https://keycloak.example/realms/healthys").audience(List.of("healthys-backend-apps"))
                    .issuedAt(now).expiresAt(now.plusSeconds(300))
                    .claim("realm_access",Map.of("roles",List.of(token.equals("admin")?"PLATFORM_ADMIN":"PATIENT")))
                    .claim("resource_access",Map.of()).build();
            };
        }
    }
}
