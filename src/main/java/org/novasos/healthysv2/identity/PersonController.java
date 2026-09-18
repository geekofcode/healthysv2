package org.novasos.healthysv2.identity;

import java.net.URI;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    PersonController(PersonService service) {
        this.service = service;
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
    PersonResponse me(@AuthenticationPrincipal Jwt jwt) {
        try {
            return service.findMe(UUID.fromString(jwt.getSubject()));
        } catch (IllegalArgumentException exception) {
            throw new BusinessRuleException(
                    "INVALID_IDENTITY_SUBJECT",
                    "The authenticated subject is not a UUID");
        }
    }
}
