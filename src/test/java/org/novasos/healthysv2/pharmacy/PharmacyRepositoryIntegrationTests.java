package org.novasos.healthysv2.pharmacy;

import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class PharmacyRepositoryIntegrationTests {
    @Autowired DispenseRepository dispenses;
    @Autowired MedicationStockRepository stocks;
    @Autowired JdbcTemplate jdbc;

    @Test void persistsDispenseAndMedicationStockWithoutOwningPrescriptionEntities() {
        var graph = graph();
        var stock = stocks.saveAndFlush(MedicationStock.create(graph.organization(), graph.medication(), "LOT-DB",
                new BigDecimal("20"), LocalDate.now().plusYears(1)));
        var dispense = dispenses.saveAndFlush(Dispense.create(graph.prescription(), graph.organization(),
                graph.pharmacist(), List.of(new Dispense.DispenseLine(
                        graph.prescriptionItem(), new BigDecimal("10"), "LOT-DB"))));
        stock.consume(new BigDecimal("10"));
        stocks.flush();
        assertThat(dispenses.findByPrescriptionIdOrderByDispensedAtDesc(graph.prescription()))
                .singleElement().satisfies(found -> assertThat(found.getItems()).hasSize(1));
        assertThat(stocks.findById(stock.getId()).orElseThrow().getQuantity()).isEqualByComparingTo("10");
        assertThat(dispense.getPrescriptionId()).isEqualTo(graph.prescription());
    }

    @Test void preventsNegativeStock() {
        var stock = MedicationStock.create(UUID.randomUUID(), UUID.randomUUID(), "LOT",
                new BigDecimal("2"), LocalDate.now().plusDays(30));
        assertThatThrownBy(() -> stock.consume(new BigDecimal("3"))).isInstanceOf(IllegalStateException.class);
    }

    private Graph graph() {
        UUID patientPerson = person(), pharmacistPerson = person(), patient = UUID.randomUUID();
        UUID pharmacist = professional(pharmacistPerson, "PHARMACIST"), organization = UUID.randomUUID();
        UUID medication = UUID.randomUUID(), prescription = UUID.randomUUID(), item = UUID.randomUUID();
        jdbc.update("insert into patient.patient(id,person_id,patient_number) values (?,?,?)",
                patient, patientPerson, "PAT-" + patient);
        jdbc.update("insert into organization.organization(id,organization_number,name) values (?,?,?)",
                organization, "ORG-" + organization, "Pharmacy");
        jdbc.update("insert into catalog.medication_catalog(id,code,name,active) values (?,?,?,?)",
                medication, "MED-" + medication.toString().substring(0, 4), "Medicine", true);
        jdbc.update("insert into prescription.prescription(id,prescription_number,patient_id,prescriber_id,organization_id) values (?,?,?,?,?)",
                prescription, "RX-" + prescription, patient, pharmacist, organization);
        jdbc.update("insert into prescription.prescription_item(id,prescription_id,medication_catalog_id,dosage,frequency,route,duration,quantity) values (?,?,?,?,?,?,?,?)",
                item, prescription, medication, "500 mg", "BID", "ORAL", "5 days", new BigDecimal("10"));
        return new Graph(prescription, item, pharmacist, organization, medication);
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

    record Graph(UUID prescription, UUID prescriptionItem, UUID pharmacist, UUID organization, UUID medication) {}
}
