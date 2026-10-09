package org.novasos.healthysv2.patient;

import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.novasos.healthysv2.patient.api.PatientProvisionResponse;
import org.novasos.healthysv2.shared.api.ApiPaths;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;

@RestController
@RequestMapping(ApiPaths.V1 + "/patients")
@Tag(name = "Patients")
class PatientProvisioningController {
    private final PatientProvisioningService service;

    PatientProvisioningController(PatientProvisioningService service) {
        this.service = service;
    }

    @PostMapping("/me/provision")
    @PreAuthorize("hasRole('PATIENT')")
    @Operation(summary = "Provision the authenticated patient after registration")
    ResponseEntity<PatientProvisionResponse> provision(
            JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();
        PatientProvisionResponse response = service.provision(
                subject(jwt),
                jwt.getClaimAsString("given_name"),
                jwt.getClaimAsString("family_name"),
                jwt.getClaimAsString("email"),
                Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")),
                new org.novasos.healthysv2.identity.api.IdentityProvisioningService.RegistrationProfile(
                        jwt.getClaimAsString("middle_name"), jwt.getClaimAsString("birthdate"),
                        jwt.getClaimAsString("gender"), jwt.getClaimAsString("phone_number"),
                        jwt.getClaimAsString("locale"),
                        new org.novasos.healthysv2.identity.api.IdentityProvisioningService.RegistrationAddress(
                                jwt.getClaimAsString("healthys_address_line1"),
                                jwt.getClaimAsString("healthys_address_line2"),
                                jwt.getClaimAsString("healthys_address_city"),
                                jwt.getClaimAsString("healthys_address_province"),
                                jwt.getClaimAsString("healthys_address_postal_code"),
                                jwt.getClaimAsString("healthys_address_country"))));
        return ResponseEntity.status(
                response.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(response);
    }

    private UUID subject(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new BusinessRuleException(
                    "INVALID_IDENTITY_SUBJECT",
                    "error.identity.subject.invalid");
        }
    }
}
