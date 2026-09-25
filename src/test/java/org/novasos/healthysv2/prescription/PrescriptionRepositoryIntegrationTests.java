package org.novasos.healthysv2.prescription;

import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test") @SpringBootTest @Import(TestcontainersConfiguration.class) @Transactional
class PrescriptionRepositoryIntegrationTests {
    @Autowired PrescriptionRepository prescriptions;
    @Autowired JdbcTemplate jdbc;

    @Test void persistsThePrescriptionAggregateAndDispensedQuantity() {
        var graph = graph();
        var prescription = Prescription.create(graph.patient(), null, graph.doctor(), graph.organization(),
                Instant.now().plusSeconds(3600));
        var item = prescription.addItem(graph.medication(), "500 mg", "BID", "ORAL", "5 days",
                new BigDecimal("10"), null);
        prescription.recordDispense(List.of(new PrescriptionDispensing.DispenseLine(
                item.getId(), new BigDecimal("4"))));
        var saved = prescriptions.saveAndFlush(prescription);

        var found = prescriptions.findById(saved.getId()).orElseThrow();
        assertThat(found.getStatus()).isEqualTo("PARTIALLY_DISPENSED");
        assertThat(found.getItems()).singleElement().satisfies(foundItem -> {
            assertThat(foundItem.getQuantityDispensed()).isEqualByComparingTo("4");
            assertThat(foundItem.remaining()).isEqualByComparingTo("6");
        });
    }

    private Graph graph() {
        UUID patientPerson = person(), doctorPerson = person(), patient = UUID.randomUUID();
        UUID doctor = professional(doctorPerson, "DOCTOR"), organization = UUID.randomUUID(), medication = UUID.randomUUID();
        jdbc.update("insert into patient.patient(id,person_id,patient_number) values (?,?,?)",
                patient, patientPerson, "PAT-" + patient);
        jdbc.update("insert into organization.organization(id,organization_number,name) values (?,?,?)",
                organization, "ORG-" + organization, "Clinic");
        jdbc.update("insert into catalog.medication_catalog(id,code,name,active) values (?,?,?,?)",
                medication, "MED-" + medication.toString().substring(0, 4), "Medicine", true);
        return new Graph(patient, doctor, organization, medication);
    }

    private UUID person() {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into identity.person(id,person_number,first_name,last_name,status) values (?,?,?,?,?)",
                id, "PER-" + id, "Test", "User", "ACTIVE");
        return id;
    }

    private UUID professional(UUID person, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into professional.professional(id,person_id,professional_number,professional_type,status) values (?,?,?,?,?)",
                id, person, "PRO-" + id, type, "ACTIVE");
        return id;
    }

    record Graph(UUID patient, UUID doctor, UUID organization, UUID medication) {}
}
