package org.novasos.healthysv2.patient;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class PatientIdentityMigrationIntegrationTests {
    @Autowired JdbcTemplate jdbc;

    @Test void preventsIdentifierDuplicatesEvenWithMissingIssuerCaseAndWhitespace() {
        UUID first = patient();
        UUID second = patient();
        jdbc.update("insert into patient.patient_identifier(patient_id,type,value,issuer) values (?, 'NATIONAL_ID', 'AB123', null)", first);
        assertThatThrownBy(() -> jdbc.update("insert into patient.patient_identifier(patient_id,type,value,issuer) values (?, 'national_id', ' ab123 ', '')", second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void theSamePatientCanRegisterAtTwoHospitalsWithoutANewPatientIdentity() {
        UUID patient = patient();
        UUID first = organization();
        UUID second = organization();
        jdbc.update("insert into patient.patient_registration(patient_id,organization_id,registration_number) values (?,?, 'LOCAL-1'),(?,?, 'LOCAL-1')",
                patient, first, patient, second);
        assertThat(jdbc.queryForObject("select count(*) from patient.patient where id=?", Integer.class, patient)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from patient.patient_registration where patient_id=?", Integer.class, patient)).isEqualTo(2);
    }

    private UUID patient() {
        UUID person = UUID.randomUUID();
        UUID patient = UUID.randomUUID();
        jdbc.update("insert into identity.person(id,person_number,first_name,last_name) values (?, ?, 'Ada', 'Lovelace')", person, "PER-"+person);
        jdbc.update("insert into patient.patient(id,person_id,patient_number) values (?,?,?)", patient, person, "PAT-"+patient);
        return patient;
    }

    private UUID organization() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into organization.organization(id,organization_number,name) values (?, ?, 'Hospital')", id, "ORG-"+id);
        return id;
    }
}
