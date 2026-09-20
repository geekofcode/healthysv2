package org.novasos.healthysv2.patient;

import java.util.Locale;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.novasos.healthysv2.shared.persistence.AuditableEntity;

@Entity
@Table(name = "patient", schema = "patient")
class Patient extends AuditableEntity {
    @Id
    private UUID id;
    @Column(name = "person_id", nullable = false, unique = true)
    private UUID personId;
    @Column(name = "patient_number", nullable = false, unique = true, length = 50)
    private String patientNumber;
    @Column(nullable = false, length = 30)
    private String status;

    protected Patient() {}

    static Patient create(UUID personId) {
        Patient patient = new Patient();
        patient.id = UUID.randomUUID();
        patient.personId = personId;
        patient.patientNumber = "PAT-" + personId.toString()
                .replace("-", "")
                .substring(0, 20)
                .toUpperCase(Locale.ROOT);
        patient.status = "ACTIVE";
        return patient;
    }

    UUID getId() { return id; }
    UUID getPersonId() { return personId; }
    String getPatientNumber() { return patientNumber; }
    String getStatus() { return status; }
}
