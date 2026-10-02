package org.novasos.healthysv2.maternalandchildhealth;

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
@Import({TestcontainersConfiguration.class,PatientMaternalChildApiIntegrationTests.Fixtures.class})
@Transactional
class PatientMaternalChildApiIntegrationTests {
    static final UUID SUBJECT=UUID.fromString("86000000-0000-0000-0000-000000000001");
    static final UUID OTHER=UUID.fromString("86000000-0000-0000-0000-000000000002");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    static final UUID CHILD=UUID.fromString("86000000-0000-0000-0000-000000000003");
    @Test void motherReadsPregnancyAndLinkedChildWithExplicitUnitsWithoutPrivateNarrative() throws Exception {
        Graph g=graph();
        read("/pregnancies","patient").andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
        read("/pregnancies/"+g.pregnancy,"patient").andExpect(status().isOk())
            .andExpect(jsonPath("$.prenatalVisits[0].weightKg").value(65.5))
            .andExpect(jsonPath("$.prenatalVisits[0].gestationalAgeWeeks").value(32))
            .andExpect(jsonPath("$.delivery.newborns[0].birthWeightKg").value(3.2))
            .andExpect(jsonPath("$.delivery.newborns[0].birthHeightCm").value(50))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE"))));
        read("/children","patient").andExpect(status().isOk()).andExpect(jsonPath("$.content[0].firstName").value("Ada"));
        read("/children/"+g.child,"patient").andExpect(status().isOk())
            .andExpect(jsonPath("$.birth.birthWeightKg").value(3.2))
            .andExpect(jsonPath("$.growthMeasurements[0].heightCm").value(55))
            .andExpect(jsonPath("$.vaccinations[0].vaccineName").value("BCG"))
            .andExpect(jsonPath("$.vaccinations[0].status").value("ADMINISTERED"))
            .andExpect(jsonPath("$.child.motherPatientId").doesNotExist());
        legacy("/pregnancies/"+g.pregnancy,"patient").andExpect(status().isOk())
            .andExpect(jsonPath("$.risks").isEmpty()).andExpect(jsonPath("$.postpartumVisits").isEmpty())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE"))));
        legacy("/child-health-records/"+g.child,"patient").andExpect(status().isOk()).andExpect(jsonPath("$.motherPatientId").isEmpty());
        legacy("/pregnancies/"+g.pregnancy,"admin").andExpect(status().isOk()).andExpect(jsonPath("$.prenatalVisits[0].notes").value("PRIVATE prenatal"));
    }
    @Test void childCannotReadMaternalPregnancyAndOtherPatientCannotReadEitherRecord() throws Exception {
        Graph g=graph();patient(OTHER);
        read("/pregnancies","child").andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        read("/pregnancies/"+g.pregnancy,"child").andExpect(status().isForbidden());
        legacy("/pregnancies/"+g.pregnancy,"child").andExpect(status().isForbidden());
        read("/children/"+g.child,"child").andExpect(status().isOk()).andExpect(jsonPath("$.birth.deliveryDate").exists()).andExpect(jsonPath("$.birth.newborns").doesNotExist());
        for(String token:List.of("other","person-"+unlinkedPatient())) {
            read("/children/"+g.child,token).andExpect(token.equals("other")?status().isForbidden():status().isNotFound());
            legacy("/child-health-records/"+g.child,token).andExpect(status().isForbidden());
            legacy("/pregnancies/"+g.pregnancy,token).andExpect(status().isForbidden());
        }
        read("/pregnancies","admin").andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/pregnancies").contentType(MediaType.APPLICATION_JSON).content("{\"motherPatientId\":\""+g.mother+"\",\"expectedDeliveryDate\":\"2027-01-01\"}").header(HttpHeaders.AUTHORIZATION,"Bearer patient")).andExpect(status().isForbidden());
    }
    @Test void paginationIsBoundedAndEmptyListsRemainValid() throws Exception {
        patient(SUBJECT);
        for(String path:List.of("/pregnancies","/children")) {
            read(path,"patient").andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(0));
            mvc.perform(get("/api/v1/patients/me/maternal-child"+path).param("size","101").header(HttpHeaders.AUTHORIZATION,"Bearer patient")).andExpect(status().isUnprocessableContent());
            mvc.perform(get("/api/v1/patients/me/maternal-child"+path).param("page","-1").header(HttpHeaders.AUTHORIZATION,"Bearer patient")).andExpect(status().isUnprocessableContent());
        }
    }
    @Test void selfChildWithNoMotherOrBirthHasNullableBirthAndEmptyClinicalLists() throws Exception {
        UUID child=patient(CHILD);
        jdbc.update("insert into maternal_child.child_health_record(child_patient_id) values (?)",child);
        read("/children/"+child,"child").andExpect(status().isOk()).andExpect(jsonPath("$.birth").isEmpty()).andExpect(jsonPath("$.vaccinations").isEmpty()).andExpect(jsonPath("$.growthMeasurements").isEmpty());
        legacy("/child-health-records/"+child,"child").andExpect(status().isOk());
    }
    private org.springframework.test.web.servlet.ResultActions read(String path,String token)throws Exception{return legacy("/patients/me/maternal-child"+path,token);}
    private org.springframework.test.web.servlet.ResultActions legacy(String path,String token)throws Exception{return mvc.perform(get("/api/v1"+path).header(HttpHeaders.AUTHORIZATION,"Bearer "+token));}
    private UUID unlinkedPatient(){UUID person=person(null);jdbc.update("insert into patient.patient(id,person_id,patient_number) values (?,?,?)",UUID.randomUUID(),person,"PAT-"+person);return person;}
    private Graph graph(){
        UUID mother=patient(SUBJECT),child=patient(CHILD),pregnancy=UUID.randomUUID(),delivery=UUID.randomUUID(),vaccine=UUID.randomUUID();
        jdbc.update("insert into maternal_child.pregnancy(id,pregnancy_number,mother_patient_id,expected_delivery_date,status) values (?,?,?,date '2026-09-01','DELIVERED')",pregnancy,"PREG-"+pregnancy,mother);
        jdbc.update("insert into maternal_child.prenatal_visit(pregnancy_id,visit_date,gestational_age_weeks,weight,systolic_pressure,diastolic_pressure,fetal_heart_rate,notes) values (?,now(),32,65.5,120,80,140,'PRIVATE prenatal')",pregnancy);
        jdbc.update("insert into maternal_child.pregnancy_risk(pregnancy_id,risk_type,notes) values (?,'PRIVATE risk','PRIVATE notes')",pregnancy);
        jdbc.update("insert into maternal_child.delivery(id,pregnancy_id,delivery_date,delivery_type,complications,notes) values (?,?,now(),'VAGINAL','PRIVATE complications','PRIVATE delivery')",delivery,pregnancy);
        jdbc.update("insert into maternal_child.newborn(delivery_id,child_patient_id,birth_weight,birth_height,head_circumference,apgar_1,apgar_5) values (?,?,3.2,50,35,8,9)",delivery,child);
        jdbc.update("insert into maternal_child.postpartum_visit(pregnancy_id,mother_patient_id,visit_date,notes) values (?,?,now(),'PRIVATE postpartum')",pregnancy,mother);
        jdbc.update("insert into maternal_child.child_health_record(child_patient_id,mother_patient_id) values (?,?)",child,mother);
        jdbc.update("insert into catalog.vaccine_catalog(id,code,name) values (?,?,?)",vaccine,"BCG-"+vaccine,"BCG");
        jdbc.update("insert into maternal_child.vaccination(child_patient_id,vaccine_catalog_id,dose_number,administered_at,next_due_date,status) values (?,?,1,now(),date '2027-01-01','ADMINISTERED')",child,vaccine);
        jdbc.update("insert into maternal_child.growth_measurement(child_patient_id,weight,height,head_circumference,bmi) values (?,4,55,36,13.22)",child);
        return new Graph(mother,child,pregnancy);
    }
    private UUID patient(UUID subject){UUID person=person(subject),patient=UUID.randomUUID();jdbc.update("insert into patient.patient(id,person_id,patient_number) values (?,?,?)",patient,person,"PAT-"+patient);return patient;}
    private UUID person(UUID subject){UUID id=UUID.randomUUID();jdbc.update("insert into identity.person(id,person_number,keycloak_user_id,first_name,last_name,status) values (?,?,?,?,?,?)",id,"PER-"+id,subject,"Ada","Patient","ACTIVE");return id;}
    private record Graph(UUID mother,UUID child,UUID pregnancy) {}
    @TestConfiguration(proxyBeanMethods=false) static class Fixtures {
        @Bean JwtDecoder jwtDecoder(){return token->{Instant now=Instant.now();String subject=token.startsWith("person-")?token.substring(7):(token.equals("other")?OTHER:token.equals("child")?CHILD:SUBJECT).toString();return Jwt.withTokenValue(token).header("alg","RS256").subject(subject).issuer("https://keycloak.example/realms/healthys").audience(List.of("healthys-backend-apps")).issuedAt(now).expiresAt(now.plusSeconds(300)).claim("realm_access",Map.of("roles",List.of(token.equals("admin")?"PLATFORM_ADMIN":"PATIENT"))).claim("resource_access",Map.of()).build();};}
    }
}
