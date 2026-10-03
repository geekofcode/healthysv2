package org.novasos.healthysv2.patient;

import java.util.Map;
import java.util.List;
import org.novasos.healthysv2.patient.api.PatientDashboardResponse.DashboardAddress;
import java.util.UUID;
import org.novasos.healthysv2.identity.api.PersonLookup;
import org.novasos.healthysv2.patient.api.PatientDashboardResponse;
import org.novasos.healthysv2.patient.api.PatientDashboardResponse.DashboardInsurance;
import org.novasos.healthysv2.patient.api.PatientDtos.*;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PatientDashboardService {
    private final PersonLookup identities;
    private final PatientRepository patients;
    private final JdbcTemplate jdbc;
    private final PatientAuditService audit;

    PatientDashboardService(PersonLookup identities, PatientRepository patients,
                            JdbcTemplate jdbc, PatientAuditService audit) {
        this.identities = identities;
        this.patients = patients;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    PatientDashboardResponse findMe(UUID subject) {
        var person = identities.findMe(subject);
        var patient = patients.findByPersonId(person.id())
                .orElseThrow(() -> new ResourceNotFoundException("Patient for person", person.id()));
        var response = new PatientDashboardResponse(person,
                new PatientSummary(patient.getId(), patient.getPersonId(), patient.getPatientNumber(),
                        patient.getBloodGroup(), patient.getRhesus(), patient.getStatus()),
                addresses(person.id()),
                patient.getInsurances().stream().map(this::insurance).toList(),
                patient.getFlags().stream().filter(PatientFlag::isActive).map(flag ->
                        new FlagResponse(flag.getId(), flag.getType(), flag.getLabel(),
                                flag.getSeverity(), true, flag.getCreatedAt())).toList(),
                patient.getAllergies().stream().filter(allergy -> "ACTIVE".equals(allergy.getStatus()))
                        .map(allergy -> new AllergyResponse(allergy.getId(), allergy.getAllergen(),
                                allergy.getType(), allergy.getReaction(), allergy.getSeverity(),
                                allergy.getStatus(), allergy.getRecordedAt(), allergy.getRecordedBy())).toList());
        audit.access(person.id(), patient.getId(), null, "PATIENT_DASHBOARD", patient.getId(),
                "READ", "PATIENT_SELF", Map.of("allowed", true));
        return response;
    }

    private List<DashboardAddress> addresses(UUID personId) {
        return jdbc.query("""
                select pa.id, pa.address_type, pa.is_primary, a.line1, a.line2, a.city,
                       a.province, a.postal_code, a.country_id
                  from identity.person_address pa join shared.address a on a.id=pa.address_id
                 where pa.person_id=? order by pa.is_primary desc, pa.id
                """, (rs, index) -> new DashboardAddress(rs.getObject("id", UUID.class),
                rs.getString("address_type"), rs.getBoolean("is_primary"), rs.getString("line1"),
                rs.getString("line2"), rs.getString("city"), rs.getString("province"),
                rs.getString("postal_code"), rs.getObject("country_id", UUID.class)), personId);
    }

    private DashboardInsurance insurance(PatientInsurance insurance) {
        String name = jdbc.query("select name from catalog.insurance_company where id=?",
                rs -> rs.next() ? rs.getString(1) : null, insurance.getInsuranceCompanyId());
        return new DashboardInsurance(insurance.getId(), insurance.getInsuranceCompanyId(), name,
                insurance.getPolicyNumber(), insurance.getMemberNumber(), insurance.getStartDate(),
                insurance.getEndDate(), insurance.isPrimary());
    }
}
