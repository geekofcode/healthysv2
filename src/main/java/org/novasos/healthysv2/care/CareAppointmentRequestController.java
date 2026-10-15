package org.novasos.healthysv2.care;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/care/appointment-requests")
public class CareAppointmentRequestController {
  private final CareAppointmentRequestService service;

  public CareAppointmentRequestController(CareAppointmentRequestService service) {
    this.service = service;
  }

  @GetMapping("/hospitals")
  public List<CareAppointmentRequestDtos.Hospital> hospitals(
      @RequestParam(required = false) String city, @RequestParam(required = false) UUID countryId) {
    return service.hospitals(city, countryId);
  }

  @GetMapping("/hospitals/{id}/specialities")
  public List<ReceptionDtos.ReceptionSpeciality> specialities(@PathVariable UUID id) {
    return service.specialities(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CareAppointmentRequestDtos.Request self(
      @Valid @RequestBody CareAppointmentRequestDtos.SelfRequest r) {
    return service.self(r);
  }

  @GetMapping("/mine")
  public List<CareAppointmentRequestDtos.Request> mine() {
    return service.mine();
  }

  @PostMapping("/staff")
  @ResponseStatus(HttpStatus.CREATED)
  public CareAppointmentRequestDtos.Request staff(
      @Valid @RequestBody CareAppointmentRequestDtos.StaffRequest r) {
    return service.staff(r);
  }

  @GetMapping
  public List<CareAppointmentRequestDtos.Request> list(
      @RequestParam(required = false) String status) {
    return service.list(status);
  }

  @PostMapping("/{id}/approve")
  public CareAppointmentRequestDtos.Request approve(
      @PathVariable UUID id, @Valid @RequestBody CareAppointmentRequestDtos.Approval r) {
    return service.approve(id, r);
  }

  @PostMapping("/{id}/reject")
  public CareAppointmentRequestDtos.Request reject(
      @PathVariable UUID id, @Valid @RequestBody CareAppointmentRequestDtos.Rejection r) {
    return service.reject(id, r);
  }
}
