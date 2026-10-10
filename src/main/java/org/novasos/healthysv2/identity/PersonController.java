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
                Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")));
        return service.findMe(subject);
    }
    @GetMapping("/me/profile")
    org.novasos.healthysv2.identity.api.PersonProfileResponse profile(JwtAuthenticationToken authentication) {
        me(authentication);
        return service.profile(UUID.fromString(authentication.getToken().getSubject()));
    }

    @org.springframework.web.bind.annotation.PutMapping("/me")
    org.novasos.healthysv2.identity.api.PersonProfileResponse updateMe(JwtAuthenticationToken authentication,
            @Valid @RequestBody org.novasos.healthysv2.identity.api.UpdatePersonProfileRequest request) {
        me(authentication);
        return service.updateMe(UUID.fromString(authentication.getToken().getSubject()), request);
    }

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    org.novasos.healthysv2.shared.api.dto.PageResponse<PersonResponse> list(
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "") String query,
            org.springframework.data.domain.Pageable pageable) {
        return org.novasos.healthysv2.shared.api.dto.PageResponse.from(service.search(query, pageable));
    }

    @GetMapping("/me/preferences")
    org.novasos.healthysv2.identity.api.PersonPreferences preferences(JwtAuthenticationToken authentication) {
        me(authentication);
        return service.preferences(UUID.fromString(authentication.getToken().getSubject()));
    }

    @org.springframework.web.bind.annotation.PutMapping("/me/preferences")
    org.novasos.healthysv2.identity.api.PersonPreferences updatePreferences(JwtAuthenticationToken authentication,
            @Valid @RequestBody org.novasos.healthysv2.identity.api.PersonPreferences request) {
        me(authentication);
        return service.updatePreferences(UUID.fromString(authentication.getToken().getSubject()), request);
    }

}
