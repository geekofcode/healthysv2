package org.novasos.healthysv2.laboratory;

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
@Import({TestcontainersConfiguration.class,PatientLaboratoryPrescriptionApiIntegrationTests.Fixtures.class})
@Transactional
class PatientLaboratoryPrescriptionApiIntegrationTests {
    static final UUID SUBJECT=UUID.fromString("86000000-0000-0000-0000-000000000001");
    static final UUID OTHER=UUID.fromString("86000000-0000-0000-0000-000000000002");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test void validatedResultsExposeValuesButNeverDraftsOrInternalNotes() throws Exception {
        Graph g=graph();
        read("/api/v1/patients/me/lab-results","patient").andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.content[0].status").value("FINAL"));
        read("/api/v1/patients/me/lab-results/"+g.result,"patient").andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].examName").value("Blood count"))
            .andExpect(jsonPath("$.items[0].parameter").value("Hemoglobin"))
            .andExpect(jsonPath("$.items[0].value").value("13.5"))
            .andExpect(jsonPath("$.result.notes").doesNotExist())
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("PRIVATE"))));
        read("/api/v1/patients/me/lab-results/"+g.draft,"patient").andExpect(status().isNotFound());
        read("/api/v1/patients/me/lab-results/"+g.invalid,"patient").andExpect(status().isNotFound());
        read("/api/v1/lab-orders/"+g.order,"patient").andExpect(status().isOk())
            .andExpect(jsonPath("$.results.length()").value(1)).andExpect(jsonPath("$.results[0].notes").isEmpty())
            .andExpect(jsonPath("$.items[0].instructions").isEmpty());
        mvc.perform(get("/api/v1/patients/me/lab-results").param("size","101").header(HttpHeaders.AUTHORIZATION,"Bearer patient")).andExpect(status().isUnprocessableContent());
    }
    @Test void prescriptionsExposeMedicationQuantitiesAndDispensationStatus() throws Exception {
        Graph g=graph();
        read("/api/v1/patients/me/prescriptions","patient").andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1));
        read("/api/v1/patients/me/prescriptions/"+g.rx,"patient").andExpect(status().isOk())
            .andExpect(jsonPath("$.prescription.status").value("PARTIALLY_DISPENSED"))
            .andExpect(jsonPath("$.prescription.expired").value(false))
            .andExpect(jsonPath("$.items[0].medicationName").value("Paracetamol"))
            .andExpect(jsonPath("$.items[0].quantity").value(10))
            .andExpect(jsonPath("$.items[0].quantityDispensed").value(4))
            .andExpect(jsonPath("$.items[0].quantityRemaining").value(6))
            .andExpect(jsonPath("$.dispensations[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$.dispensations[0].items[0].quantityDispensed").value(4));
        jdbc.update("update prescription.prescription set expires_at=now()-interval '1 day' where id=?",g.rx);
        read("/api/v1/patients/me/prescriptions/"+g.rx,"patient").andExpect(status().isOk()).andExpect(jsonPath("$.prescription.expired").value(true));
        jdbc.update("update prescription.prescription set status='CANCELLED' where id=?",g.rx);
        read("/api/v1/patients/me/prescriptions/"+g.rx,"patient").andExpect(status().isOk()).andExpect(jsonPath("$.prescription.status").value("CANCELLED")).andExpect(jsonPath("$.prescription.expired").value(false));
    }
    @Test void strictOwnershipAndKeycloakLinkApplyToSelfAndLegacyReads() throws Exception {
        Graph g=graph();patient(OTHER);
        for(String path:List.of("/api/v1/patients/me/lab-results/"+g.result,"/api/v1/lab-orders/"+g.order,"/api/v1/patients/me/prescriptions/"+g.rx,"/api/v1/prescriptions/"+g.rx,"/api/v1/prescriptions/"+g.rx+"/dispenses"))
            read(path,"other").andExpect(status().isForbidden());
        UUID unlinked=person(null);jdbc.update("insert into patient.patient(id,person_id,patient_number) values (?,?,?)",UUID.randomUUID(),unlinked,"PAT-"+unlinked);
        read("/api/v1/patients/me/lab-results","person:"+unlinked).andExpect(status().isNotFound());
        read("/api/v1/prescriptions/"+g.rx,"person:"+unlinked).andExpect(status().isNotFound());
        read("/api/v1/patients/me/prescriptions","admin").andExpect(status().isForbidden());
    }
    private org.springframework.test.web.servlet.ResultActions read(String path,String token)throws Exception{return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION,"Bearer "+token));}
    private Graph graph(){
        UUID patient=patient(SUBJECT),doctorPerson=person(null),professional=UUID.randomUUID(),organization=UUID.randomUUID(),exam=UUID.randomUUID(),order=UUID.randomUUID(),orderItem=UUID.randomUUID(),result=UUID.randomUUID(),draft=UUID.randomUUID(),invalid=UUID.randomUUID(),rx=UUID.randomUUID(),rxItem=UUID.randomUUID(),medication=UUID.randomUUID(),dispense=UUID.randomUUID();
        jdbc.update("insert into professional.professional(id,person_id,professional_number,professional_type,status) values (?,?,?,?,?)",professional,doctorPerson,"PRO-"+professional,"DOCTOR","ACTIVE");
        jdbc.update("insert into organization.organization(id,organization_number,name) values (?,?,?)",organization,"ORG-"+organization,"Hospital Pharmacy");
        jdbc.update("insert into catalog.laboratory_exam_catalog(id,code,name) values (?,?,?)",exam,"EX-"+exam,"Blood count");
        jdbc.update("insert into laboratory.lab_order(id,order_number,patient_id,ordering_professional_id,laboratory_organization_id,status) values (?,?,?,?,?,?)",order,"LAB-"+order,patient,professional,organization,"COMPLETED");
        jdbc.update("insert into laboratory.lab_order_item(id,lab_order_id,lab_exam_catalog_id,instructions,status) values (?,?,?,?,?)",orderItem,order,exam,"PRIVATE instructions","COMPLETED");
        jdbc.update("insert into laboratory.lab_result(id,result_number,lab_order_id,status,notes,validated_by,validated_at) values (?,?,?,?,?,?,now())",result,"RES-"+result,order,"FINAL","PRIVATE note",professional);
        jdbc.update("insert into laboratory.lab_result(id,result_number,lab_order_id,status,notes) values (?,?,?,?,?)",draft,"RES-"+draft,order,"DRAFT","PRIVATE draft");
        jdbc.update("insert into laboratory.lab_result(id,result_number,lab_order_id,status) values (?,?,?,?)",invalid,"RES-"+invalid,order,"FINAL");
        jdbc.update("insert into laboratory.lab_result_item(lab_result_id,lab_order_item_id,parameter,value,unit,reference_min,reference_max,abnormal_flag) values (?,?,?,?,?,?,?,?)",result,orderItem,"Hemoglobin","13.5","g/dL",12,16,"NORMAL");
        jdbc.update("insert into catalog.medication_catalog(id,code,name,generic_name,form,strength) values (?,?,?,?,?,?)",medication,"MED-"+medication,"Paracetamol","Acetaminophen","Tablet","500 mg");
        jdbc.update("insert into prescription.prescription(id,prescription_number,patient_id,prescriber_id,organization_id,status,expires_at) values (?,?,?,?,?,?,now()+interval '30 days')",rx,"RX-"+rx,patient,professional,organization,"PARTIALLY_DISPENSED");
        jdbc.update("insert into prescription.prescription_item(id,prescription_id,medication_catalog_id,dosage,frequency,route,duration,quantity,quantity_dispensed,instructions) values (?,?,?,?,?,?,?,?,?,?)",rxItem,rx,medication,"500 mg","Twice daily","ORAL","5 days",10,4,"After food");
        jdbc.update("insert into pharmacy.dispense(id,dispense_number,prescription_id,pharmacy_organization_id,pharmacist_id,status) values (?,?,?,?,?,?)",dispense,"DSP-"+dispense,rx,organization,professional,"COMPLETED");
        jdbc.update("insert into pharmacy.dispense_item(dispense_id,prescription_item_id,quantity_dispensed,batch_number) values (?,?,?,?)",dispense,rxItem,4,"BATCH");
        return new Graph(order,result,draft,invalid,rx);
    }
    private UUID patient(UUID subject){UUID person=person(subject),patient=UUID.randomUUID();jdbc.update("insert into patient.patient(id,person_id,patient_number) values (?,?,?)",patient,person,"PAT-"+patient);return patient;}
    private UUID person(UUID subject){UUID id=UUID.randomUUID();jdbc.update("insert into identity.person(id,person_number,keycloak_user_id,first_name,last_name,status) values (?,?,?,?,?,?)",id,"PER-"+id,subject,"Ada","Patient","ACTIVE");return id;}
    private record Graph(UUID order,UUID result,UUID draft,UUID invalid,UUID rx) {}
    @TestConfiguration(proxyBeanMethods=false) static class Fixtures {
        @Bean JwtDecoder jwtDecoder(){return token->{Instant now=Instant.now();String subject=token.startsWith("person:")?token.substring(7):(token.equals("other")?OTHER:SUBJECT).toString();return Jwt.withTokenValue(token).header("alg","RS256").subject(subject).issuer("https://keycloak.example/realms/healthys").audience(List.of("healthys-backend-apps")).issuedAt(now).expiresAt(now.plusSeconds(300)).claim("realm_access",Map.of("roles",List.of(token.equals("admin")?"PLATFORM_ADMIN":"PATIENT"))).claim("resource_access",Map.of()).build();};}
    }
}
