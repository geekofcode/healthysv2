package org.novasos.healthysv2.identity;

import java.net.URI;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.novasos.healthysv2.identity.api.IdentityProvisioningService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.novasos.healthysv2.shared.api.ApiPaths;
import org.novasos.healthysv2.identity.api.CreatePersonRequest;
import org.novasos.healthysv2.identity.api.PersonResponse;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;

@RestController
@RequestMapping(ApiPaths.V1 + "/persons")
@Tag(name = "Persons")
class PersonController {

    private final PersonService service;

    private final IdentityProvisioningService provisioning;

    PersonController(PersonService service, IdentityProvisioningService provisioning) {
        this.service = service;
        this.provisioning = provisioning;
    }

    @PostMapping
    @PreAuthorize(
            "hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','HOSPITAL_AGENT')")
    @Operation(summary = "Create a person")
    ResponseEntity<PersonResponse> create(
            @Valid @RequestBody CreatePersonRequest request) {
        PersonResponse response = service.create(request);
        return ResponseEntity
                .created(URI.create(
                        ApiPaths.V1 + "/persons/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize(
            "hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','HOSPITAL_AGENT')")
    @Operation(summary = "Find a person by identifier")
    PersonResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @GetMapping("/me")
    @Operation(summary = "Return the person linked to the authenticated user")
    PersonResponse me(JwtAuthenticationToken authentication) {
        Jwt jwt = authentication.getToken();
        UUID subject;
        try {
            subject = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException exception) {
            throw new BusinessRuleException(
                    "INVALID_IDENTITY_SUBJECT",
                    "error.identity.subject.invalid");
        }
        provisioning.provisionIdentity(subject,
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
        return service.findMe(subject);
    }
}
