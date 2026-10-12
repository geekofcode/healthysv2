package org.novasos.healthysv2.patient;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PatientRepositoryIntegrationTests {
    @Autowired PatientRepository repository;
    @Autowired JdbcTemplate jdbc;

    @Test
    void persistsAndFindsPatientByPerson() {
        UUID personId = insertPerson();
        UUID company = UUID.randomUUID();
        UUID organization = UUID.randomUUID();
        jdbc.update("insert into catalog.insurance_company(id,code,name) values (?,?,?)", company, "INS-" + company, "Mutual");
        jdbc.update("insert into organization.organization(id,organization_number,name) values (?,?,?)", organization, "ORG-" + organization, "Hospital");
        Patient patient = Patient.create(personId, "A+", "POSITIVE", null, "Teacher", null);
        patient.addIdentifier("NATIONAL_ID", "ID-42", "State", null, null);
        patient.addInsurance(company, "POL-42", "MEM-42", LocalDate.now(), null, true);
        patient.addRegistration(organization, "REG-42", null, null);
        patient.addAllergy("Latex", "CONTACT", "Rash", "MEDIUM", null, null, personId);
        patient.addChronicDisease(null, LocalDate.now(), null, "Monitored");
        patient.addMedicalHistory("Asthma", LocalDate.now().minusYears(2), null, null);
        patient.addSurgicalHistory("Appendectomy", LocalDate.now().minusYears(5), organization, null);
        patient.addFamilyHistory("Parent", "Diabetes", null);
        patient.addDisability("MOBILITY", "Temporary", LocalDate.now(), null);
        patient.addNote(personId, "CLINICAL", "Follow up", null);
        patient.addFlag("RISK", "Fall risk", "HIGH", true, null);
        patient.setEmergencyProfile("ICE-42", true, true, true, false, true, true);
        Patient saved = repository.saveAndFlush(patient);
        assertThat(repository.findByPersonId(personId))
                .contains(saved);
        Patient reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getIdentifiers()).hasSize(1);
        assertThat(reloaded.getInsurances()).hasSize(1);
        assertThat(reloaded.getRegistrations()).hasSize(1);
        assertThat(reloaded.getAllergies()).hasSize(1);
        assertThat(reloaded.getChronicDiseases()).hasSize(1);
        assertThat(reloaded.getMedicalHistories()).hasSize(1);
        assertThat(reloaded.getSurgicalHistories()).hasSize(1);
        assertThat(reloaded.getFamilyHistories()).hasSize(1);
        assertThat(reloaded.getDisabilities()).hasSize(1);
        assertThat(reloaded.getNotes()).hasSize(1);
        assertThat(reloaded.getFlags()).hasSize(1);
        assertThat(reloaded.getEmergencyProfile()).isNotNull();
    }

    @Test
    void independentListRequiresLivePersonalConsentBeforePagination() {
        UUID person = insertPerson(), professional = UUID.randomUUID();
        jdbc.update("insert into professional.professional(id,person_id,professional_number,professional_type) values (?,?,?,'DOCTOR')",
                professional, person, "PRO-"+professional);
        Patient allowed = repository.saveAndFlush(Patient.create(insertPerson()));
        Patient denied = repository.saveAndFlush(Patient.create(insertPerson()));
        for (Patient patient : java.util.List.of(allowed, denied)) {
            jdbc.update("insert into patient.care_relationship(patient_id,professional_id,relationship_type) values (?,?,'PRIMARY')",
                    patient.getId(), professional);
        }
        jdbc.update("insert into patient.consent(patient_id,grantee_person_id,scope) values (?,?,'MEDICAL_RECORD')", allowed.getId(), person);
        var page = repository.searchScoped("", null, person, true, org.springframework.data.domain.PageRequest.of(0, 1, org.springframework.data.domain.Sort.by("patient_number")));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(Patient::getId).containsExactly(allowed.getId());
        jdbc.update("update patient.consent set revoked_at=clock_timestamp(),status='REVOKED' where patient_id=?", allowed.getId());
        assertThat(repository.searchScoped("", null, person, true, org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements()).isZero();
    }

    @Test
    void hospitalListNeverIncludesPatientsRegisteredOnlyElsewhere() {
        UUID own = UUID.randomUUID(), other = UUID.randomUUID();
        for (UUID organization : java.util.List.of(own, other)) {
            jdbc.update("insert into organization.organization(id,organization_number,name) values (?,?,?)", organization, "ORG-"+organization,"Hospital");
        }
        Patient allowed = repository.saveAndFlush(Patient.create(insertPerson()));
        Patient denied = repository.saveAndFlush(Patient.create(insertPerson()));
        jdbc.update("insert into patient.patient_registration(patient_id,organization_id,registration_number) values (?,?,?)", allowed.getId(),own,"REG-"+allowed.getId());
        jdbc.update("insert into patient.patient_registration(patient_id,organization_id,registration_number) values (?,?,?)", denied.getId(),other,"REG-"+denied.getId());
        var page = repository.searchScoped("", own, null, false, org.springframework.data.domain.PageRequest.of(0, 1, org.springframework.data.domain.Sort.by("patient_number")));
        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent()).extracting(Patient::getId).containsExactly(allowed.getId());
    }

    private UUID insertPerson() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO identity.person
                    (id, person_number, first_name, last_name, status)
                VALUES (?, ?, 'Test', 'Patient', 'ACTIVE')
                """, id, "PER-" + id);
        return id;
    }
}
