package org.novasos.healthysv2.prescription;

import static org.novasos.healthysv2.prescription.api.PrescriptionDtos.CreatePrescriptionRequest;
import static org.novasos.healthysv2.prescription.api.PrescriptionDtos.PrescriptionItemResponse;
import static org.novasos.healthysv2.prescription.api.PrescriptionDtos.PrescriptionResponse;
import static org.novasos.healthysv2.prescription.api.PrescriptionDtos.PrescriptionSummary;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.novasos.healthysv2.patient.PatientAccessService;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;
import org.novasos.healthysv2.shared.api.error.ConflictException;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class PrescriptionService implements PrescriptionDispensing {
    private final PrescriptionRepository prescriptions;
    private final JdbcTemplate jdbc;
    private final PatientAccessService access;

    PrescriptionService(PrescriptionRepository prescriptions, JdbcTemplate jdbc, PatientAccessService access) {
        this.prescriptions = prescriptions;
        this.jdbc = jdbc;
        this.access = access;
    }

    PrescriptionResponse create(CreatePrescriptionRequest request) {
        access.requireAccess(request.patientId(), "PRESCRIPTIONS", "WRITE");
        validatePrescription(request);
        var prescription = Prescription.create(request.patientId(), request.consultationId(),
                request.prescriberId(), request.organizationId(), request.expiresAt());
        request.items().forEach(item -> prescription.addItem(item.medicationCatalogId(), item.dosage(),
                item.frequency(), item.route(), item.duration(), item.quantity(), item.instructions()));
        prescriptions.saveAndFlush(prescription);
        audit(prescription, "CREATE_PRESCRIPTION");
        return response(prescription);
    }

    @Transactional(readOnly = true)
    PrescriptionResponse find(UUID id) {
        var prescription = get(id);
        access.requireAccess(prescription.getPatientId(), "PRESCRIPTIONS", "READ");
        return response(prescription);
    }

    @Transactional(readOnly = true)
    PageResponse<PrescriptionSummary> search(UUID patient, UUID organization, String status, Pageable pageable) {
        if (patient != null) {
            access.requireAccess(patient, "PRESCRIPTIONS", "READ");
        }
        return PageResponse.from(prescriptions.search(patient, organization, normalize(status), pageable)
                .map(this::summary));
    }

    PrescriptionResponse cancel(UUID id) {
        var prescription = forWrite(id);
        try {
            prescription.cancel();
            prescriptions.flush();
            audit(prescription, "CANCEL_PRESCRIPTION");
            return response(prescription);
        } catch (IllegalStateException exception) {
            throw rule("PRESCRIPTION_CANNOT_CANCEL", "error.pharmacy.cannot-cancel");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void verifyReadAccess(UUID prescriptionId) {
        var prescription = get(prescriptionId);
        access.requireAccess(prescription.getPatientId(), "PRESCRIPTIONS", "READ");
    }

    @Override
    public PrescriptionForDispensing prepare(UUID prescriptionId) {
        var prescription = forWrite(prescriptionId);
        if (prescription.expired()) {
            throw rule("PRESCRIPTION_EXPIRED", "error.pharmacy.expired");
        }
        if (Set.of("DISPENSED", "CANCELLED").contains(prescription.getStatus())) {
            throw rule("PRESCRIPTION_CLOSED", "error.pharmacy.closed");
        }
        return new PrescriptionForDispensing(prescription.getId(), prescription.getPatientId(),
                prescription.getOrganizationId(), prescription.getStatus(), prescription.getItems().stream()
                .map(item -> new ItemForDispensing(item.getId(), item.getMedicationId(), item.remaining()))
                .toList());
    }

    @Override
    public void record(UUID prescriptionId, List<DispenseLine> lines) {
        var prescription = forWrite(prescriptionId);
        try {
            prescription.recordDispense(lines);
            prescriptions.flush();
            audit(prescription, "RECORD_DISPENSE");
        } catch (IllegalArgumentException exception) {
            throw rule("INVALID_DISPENSE_QUANTITY", "error.pharmacy.dispense-quantity");
        } catch (IllegalStateException exception) {
            throw rule(prescription.expired() ? "PRESCRIPTION_EXPIRED" : "PRESCRIPTION_CLOSED",
                    prescription.expired() ? "error.pharmacy.expired" : "error.pharmacy.closed");
        }
    }

    private void validatePrescription(CreatePrescriptionRequest request) {
        require("patient.patient", request.patientId(), "Patient");
        require("professional.professional", request.prescriberId(), "Professional");
        if (request.organizationId() != null) {
            require("organization.organization", request.organizationId(), "Organization");
        }
        if (request.consultationId() != null && !exists(
                "select count(*) from consultation.consultation where id=? and patient_id=? and professional_id=?",
                request.consultationId(), request.patientId(), request.prescriberId())) {
            throw rule("CONSULTATION_PRESCRIPTION_MISMATCH", "error.pharmacy.consultation");
        }
        var medications = new HashSet<UUID>();
        request.items().forEach(item -> {
            requireMedication(item.medicationCatalogId());
            if (!medications.add(item.medicationCatalogId())) {
                throw new ConflictException("MEDICATION_ALREADY_PRESCRIBED", "error.pharmacy.medication-exists");
            }
        });
    }

    private Prescription forWrite(UUID id) {
        var prescription = prescriptions.lock(id).orElseThrow(() -> notFound("Prescription", id));
        access.requireAccess(prescription.getPatientId(), "PRESCRIPTIONS", "WRITE");
        return prescription;
    }

    private Prescription get(UUID id) {
        return prescriptions.findById(id).orElseThrow(() -> notFound("Prescription", id));
    }

    private void requireMedication(UUID id) {
        if (!exists("select count(*) from catalog.medication_catalog where id=? and active=true", id)) {
            throw notFound("MedicationCatalog", id);
        }
    }

    private void require(String table, UUID id, String resource) {
        if (!exists("select count(*) from " + table + " where id=?", id)) {
            throw notFound(resource, id);
        }
    }

    private boolean exists(String sql, Object... arguments) {
        return Objects.requireNonNull(jdbc.queryForObject(sql, Integer.class, arguments)) > 0;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private PrescriptionSummary summary(Prescription prescription) {
        return new PrescriptionSummary(prescription.getId(), prescription.getNumber(), prescription.getPatientId(),
                prescription.getOrganizationId(), prescription.getPrescribedAt(), prescription.getExpiresAt(),
                prescription.getStatus());
    }

    private PrescriptionResponse response(Prescription prescription) {
        return new PrescriptionResponse(prescription.getId(), prescription.getNumber(), prescription.getPatientId(),
                prescription.getConsultationId(), prescription.getPrescriberId(), prescription.getOrganizationId(),
                prescription.getPrescribedAt(), prescription.getExpiresAt(), prescription.getStatus(),
                prescription.getItems().stream().map(this::item).toList());
    }

    private PrescriptionItemResponse item(PrescriptionItem item) {
        String[] medication = jdbc.query("select code,name from catalog.medication_catalog where id=?",
                result -> result.next() ? new String[]{result.getString(1), result.getString(2)}
                        : new String[]{null, null}, item.getMedicationId());
        return new PrescriptionItemResponse(item.getId(), item.getMedicationId(), medication[0], medication[1],
                item.getDosage(), item.getFrequency(), item.getRoute(), item.getDuration(), item.getQuantity(),
                item.getQuantityDispensed(), item.remaining(), item.getInstructions());
    }

    private void audit(Prescription prescription, String action) {
        jdbc.update("insert into audit.audit_log(actor_person_id,organization_id,module,entity_type,entity_id,action,new_value) "
                        + "values (?,?,?,?,?,?,?::jsonb)", actor(), prescription.getOrganizationId(), "PRESCRIPTION",
                "Prescription", prescription.getId(), action,
                "{\"status\":\"" + prescription.getStatus() + "\"}");
    }

    private UUID actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            return null;
        }
        try {
            UUID subject = UUID.fromString(jwt.getToken().getSubject());
            return jdbc.query("select id from identity.person where keycloak_user_id=? or id=? limit 1",
                    result -> result.next() ? (UUID) result.getObject(1) : null, subject, subject);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private ResourceNotFoundException notFound(String resource, Object id) {
        return new ResourceNotFoundException(resource, id);
    }

    private BusinessRuleException rule(String code, String messageKey) {
        return new BusinessRuleException(code, messageKey);
    }
}
