package org.novasos.healthysv2.consultation;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test") @SpringBootTest @Import(TestcontainersConfiguration.class) @Transactional
class ConsultationListIntegrationTests {
    @Autowired ConsultationListService lists;
    @Autowired ConsultationRepository consultations;
    @Autowired JdbcTemplate jdbc;
    @AfterEach void clearAuthentication(){SecurityContextHolder.clearContext();}

    @Test void filtersBeforePaginationAndOnlyReturnsSummaries() {
        UUID person=person(),professional=professional(person),patient=patient();
        consultation(patient,professional,null,"IN_PROGRESS");
        consultation(patient,professional,null,"COMPLETED");
        authenticate(person,"PLATFORM_ADMIN");
        var page=lists.list("Jane","COMPLETED",patient,0,1);
        assertThat(page.page().totalElements()).isEqualTo(1);
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().status()).isEqualTo("COMPLETED");
        assertThat(lists.list("Jane","IN_PROGRESS",patient,0,1).page().totalElements()).isEqualTo(1);
    }

    @Test void independentListsRequireCurrentCareAndConsentAndOwnedConsultation() {
        UUID person=person(),professional=professional(person),patient=patient(),unauthorized=patient();
        UUID visible=consultation(patient,professional,null,"IN_PROGRESS");
        consultation(unauthorized,professional,null,"IN_PROGRESS");
        UUID otherPro=professional(person());
        consultation(patient,otherPro,null,"IN_PROGRESS");
        jdbc.update("insert into patient.care_relationship(patient_id,professional_id,relationship_type,start_date,status) values (?,?,'PRIMARY',now()-interval '1 day','ACTIVE')",patient,professional);
        UUID consent=UUID.randomUUID();
        jdbc.update("insert into patient.consent(id,patient_id,grantee_person_id,scope,granted_at,status) values (?,?,?,'MEDICAL_RECORD',now()-interval '1 day','ACTIVE')",consent,patient,person);
        authenticate(person,"DOCTOR");
        var page=lists.list(null,null,null,0,1);
        assertThat(page.page().totalElements()).isEqualTo(1);
        assertThat(page.content().getFirst().id()).isEqualTo(visible);
        jdbc.update("update patient.consent set revoked_at=now() where id=?",consent);
        assertThat(lists.list(null,null,null,0,20).content()).isEmpty();
    }

    @Test void hospitalAdministratorCannotEnumerateOtherOrganizationsOrUnregisteredPatients() {
        UUID person=person(),professional=professional(person),patient=patient(),unregistered=patient();
        UUID organization=UUID.randomUUID(),other=UUID.randomUUID();
        jdbc.update("insert into organization.organization(id,organization_number,name) values (?,?,'Clinic'),(?,?,'Other')",organization,"ORG-"+organization,other,"ORG-"+other);
        consultation(patient,professional,organization,"IN_PROGRESS");
        consultation(patient,professional,other,"IN_PROGRESS");
        consultation(unregistered,professional,organization,"IN_PROGRESS");
        jdbc.update("insert into patient.patient_registration(patient_id,organization_id,registration_number,status) values (?,?,?,'ACTIVE')",patient,organization,"REG-"+patient);
        authenticate(person,"HOSPITAL_ADMIN",organization);
        assertThat(lists.list(null,null,null,0,20).page().totalElements()).isEqualTo(1);
    }

    private UUID consultation(UUID patient,UUID professional,UUID organization,String status){
        var consultation=Consultation.start(patient,professional,organization,null,null,"GENERAL",null);
        if("COMPLETED".equals(status))consultation.complete();
        consultations.saveAndFlush(consultation);return consultation.getId();
    }
    private UUID person(){UUID id=UUID.randomUUID();jdbc.update("insert into identity.person(id,keycloak_user_id,person_number,first_name,last_name,status) values (?,?,?,'Jane','Doe','ACTIVE')",id,id,"PER-"+id);return id;}
    private UUID professional(UUID person){UUID id=UUID.randomUUID();jdbc.update("insert into professional.professional(id,person_id,professional_number,professional_type,status) values (?,?,?,'DOCTOR','ACTIVE')",id,person,"PRO-"+id);return id;}
    private UUID patient(){UUID id=UUID.randomUUID();jdbc.update("insert into patient.patient(id,person_id,patient_number) values (?,?,?)",id,person(),"PAT-"+id);return id;}
    private void authenticate(UUID person,String role){authenticate(person,role,null);}
    private void authenticate(UUID person,String role,UUID organization){
        var builder=Jwt.withTokenValue("test").header("alg","RS256").subject(person.toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
        if(organization!=null)builder.claim("healthys_organization_id",organization.toString());
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build(),List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
}
