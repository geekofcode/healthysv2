package org.novasos.healthysv2.appointment;

import static org.novasos.healthysv2.appointment.api.PatientAppointmentDtos.*;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/patients/me/appointments")
@PreAuthorize("hasRole('PATIENT')")
@Tag(name = "Patient appointments")
class PatientAppointmentController {
    private final PatientAppointmentService service;
    PatientAppointmentController(PatientAppointmentService service) { this.service = service; }

    @GetMapping
    @Operation(operationId = "getMyPatientAppointments")
    PageResponse<PatientAppointmentResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "upcoming") String view, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return service.list(subject(jwt), view, page, size); }

    @GetMapping("/{id}")
    @Operation(operationId = "getMyPatientAppointment")
    PatientAppointmentResponse detail(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return service.detail(subject(jwt), id);
    }

    @GetMapping("/booking-options")
    @Operation(operationId = "getMyPatientAppointmentBookingOptions")
    BookingOptions options(@AuthenticationPrincipal Jwt jwt) { return service.options(subject(jwt)); }

    @GetMapping("/availability")
    @Operation(operationId = "getMyPatientAppointmentAvailability")
    AvailableSlots availability(@AuthenticationPrincipal Jwt jwt, @RequestParam UUID organizationId,
            @RequestParam UUID professionalId, @RequestParam Instant from, @RequestParam Instant to,
            @RequestParam(required = false) UUID excludeAppointmentId) {
        return service.availability(subject(jwt), organizationId, professionalId, from, to, excludeAppointmentId);
    }

    @PostMapping
    @Operation(operationId = "createMyPatientAppointment")
    ResponseEntity<PatientAppointmentResponse> create(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PatientBookingRequest request) {
        var response = service.create(subject(jwt), request);
        return ResponseEntity.created(URI.create("/api/v1/patients/me/appointments/" + response.id())).body(response);
    }

    @PostMapping("/{id}/cancel")
    @Operation(operationId = "cancelMyPatientAppointment")
    PatientAppointmentResponse cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody PatientCancelRequest request) { return service.cancel(subject(jwt), id, request); }

    @PostMapping("/{id}/reschedule")
    @Operation(operationId = "rescheduleMyPatientAppointment")
    PatientAppointmentResponse reschedule(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id,
            @Valid @RequestBody PatientRescheduleRequest request) { return service.reschedule(subject(jwt), id, request); }

    private UUID subject(Jwt jwt) {
        try { return UUID.fromString(jwt.getSubject()); }
        catch (IllegalArgumentException exception) {
            throw new BusinessRuleException("INVALID_IDENTITY_SUBJECT", "error.identity.subject.invalid");
        }
    }
}
