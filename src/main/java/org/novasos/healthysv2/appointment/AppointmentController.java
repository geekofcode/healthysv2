package org.novasos.healthysv2.appointment;
import static org.novasos.healthysv2.appointment.api.AppointmentDtos.*;
import java.net.URI;import java.time.Instant;import java.util.*;import jakarta.validation.Valid;import io.swagger.v3.oas.annotations.*;import io.swagger.v3.oas.annotations.tags.Tag;import org.novasos.healthysv2.shared.api.ApiPaths;import org.novasos.healthysv2.shared.api.dto.PageResponse;import org.springframework.data.domain.Pageable;import org.springframework.http.ResponseEntity;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping(ApiPaths.V1+"/appointments") @Tag(name="Appointments",description="Appointments, participants, status history and waiting queue") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','HOSPITAL_AGENT','DOCTOR','NURSE','PATIENT')")
class AppointmentController{
 private static final String STAFF="hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','HOSPITAL_AGENT','DOCTOR','NURSE')";private final AppointmentService service;AppointmentController(AppointmentService service){this.service=service;}
 @GetMapping @PreAuthorize(STAFF) PageResponse<AppointmentSummary> search(@RequestParam(required=false)UUID patientId,@RequestParam(required=false)UUID professionalId,@RequestParam(required=false)String status,@RequestParam(required=false)Instant from,@RequestParam(required=false)Instant to,Pageable pageable){return service.search(patientId,professionalId,status,from,to,pageable);}
 @GetMapping("/me") PageResponse<AppointmentSummary> mine(@RequestParam(required=false)Instant from,@RequestParam(required=false)Instant to,Pageable pageable){return service.mine(from,to,pageable);}
 @GetMapping("/{id}") AppointmentResponse find(@PathVariable UUID id){return service.find(id);}
 @PostMapping ResponseEntity<AppointmentResponse> create(@Valid @RequestBody AppointmentRequest r){var out=service.create(r);return ResponseEntity.created(URI.create(ApiPaths.V1+"/appointments/"+out.id())).body(out);}
 @PutMapping("/{id}") AppointmentResponse update(@PathVariable UUID id,@Valid @RequestBody AppointmentUpdateRequest r){return service.update(id,r);}
 @PostMapping("/{id}/cancel") AppointmentResponse cancel(@PathVariable UUID id,@Valid @RequestBody CancelRequest r){return service.cancel(id,r);}
 @PostMapping("/{id}/reschedule") AppointmentResponse reschedule(@PathVariable UUID id,@Valid @RequestBody RescheduleRequest r){return service.reschedule(id,r);}
 @PostMapping("/{id}/status") @PreAuthorize(STAFF) AppointmentResponse status(@PathVariable UUID id,@Valid @RequestBody StatusRequest r){return service.status(id,r);}
 @PostMapping("/{id}/participants") ResponseEntity<ParticipantResponse> participant(@PathVariable UUID id,@Valid @RequestBody ParticipantRequest r){return ResponseEntity.status(201).body(service.addParticipant(id,r));}
 @PatchMapping("/{appointment}/participants/{id}") ParticipantResponse participantStatus(@PathVariable UUID appointment,@PathVariable UUID id,@Valid @RequestBody ParticipantStatusRequest r){return service.participantStatus(appointment,id,r);}
 @PostMapping("/{id}/queue/check-in") @PreAuthorize(STAFF) ResponseEntity<QueueResponse> checkIn(@PathVariable UUID id){return ResponseEntity.status(201).body(service.checkIn(id));}
 @PostMapping("/{appointment}/queue/{id}/call") @PreAuthorize(STAFF) QueueResponse call(@PathVariable UUID appointment,@PathVariable UUID id){return service.call(appointment,id);}
 @PostMapping("/{appointment}/queue/{id}/complete") @PreAuthorize(STAFF) QueueResponse complete(@PathVariable UUID appointment,@PathVariable UUID id){return service.complete(appointment,id);}
 @GetMapping("/availability") @Operation(summary="Check professional availability") AvailabilityResponse availability(@RequestParam UUID professionalId,@RequestParam UUID organizationId,@RequestParam Instant start,@RequestParam Instant end){return service.availability(professionalId,organizationId,start,end);}
}
