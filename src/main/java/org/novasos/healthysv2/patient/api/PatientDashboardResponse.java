package org.novasos.healthysv2.patient.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.novasos.healthysv2.identity.api.PersonResponse;
import org.novasos.healthysv2.patient.api.PatientDtos.AllergyResponse;
import org.novasos.healthysv2.patient.api.PatientDtos.FlagResponse;
import org.novasos.healthysv2.patient.api.PatientDtos.PatientSummary;

/** Minimal patient-facing summary; clinical notes and histories are intentionally absent. */
public record PatientDashboardResponse(
        PersonResponse person,
        PatientSummary patient,
        List<DashboardAddress> addresses,
        List<DashboardInsurance> insurances,
        List<FlagResponse> flags,
        List<AllergyResponse> allergies) {
    public record DashboardAddress(
            UUID id, String addressType, boolean primary, String line1, String line2,
            String city, String province, String postalCode, UUID countryId) {}

    public record DashboardInsurance(
            UUID id, UUID insuranceCompanyId, String insuranceCompanyName,
            String policyNumber, String memberNumber, LocalDate startDate,
            LocalDate endDate, boolean primary) {}
}
