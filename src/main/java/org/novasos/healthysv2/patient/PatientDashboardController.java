package org.novasos.healthysv2.patient;

import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.novasos.healthysv2.patient.api.PatientDashboardResponse;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/patients/me")
@Tag(name = "Patient dashboard")
class PatientDashboardController {
    private final PatientDashboardService service;

    PatientDashboardController(PatientDashboardService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('PATIENT')")
    @Operation(operationId = "getMyPatientDashboard", summary = "Read the authenticated patient's dashboard")
    PatientDashboardResponse dashboard(@AuthenticationPrincipal Jwt jwt) {
        UUID subject;
        try {
            subject = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new BusinessRuleException("INVALID_IDENTITY_SUBJECT", "error.identity.subject.invalid");
        }
        return service.findMe(subject);
    }
}
