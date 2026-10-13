package org.novasos.healthysv2.care;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/care/episodes")
public class ReceptionController {
    private final ReceptionService service;
    ReceptionController(ReceptionService service) { this.service=service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public ReceptionDtos.Episode open(@Valid @RequestBody ReceptionDtos.OpenEpisode request) { return service.open(request); }
    @GetMapping
    public List<ReceptionDtos.Episode> list(@RequestParam(required=false) UUID patientId) { return service.list(patientId); }
    @GetMapping("/reception-options")
    public ReceptionDtos.ReceptionOptions receptionOptions() { return service.receptionOptions(); }
    @GetMapping("/{id}")
    public ReceptionDtos.Episode detail(@PathVariable UUID id,@RequestParam(defaultValue="false") boolean includeIntake) { return service.detail(id,includeIntake); }
    @GetMapping("/{id}/intake")
    public ReceptionDtos.Intake readIntake(@PathVariable UUID id) { return service.readIntake(id); }
    @PutMapping("/{id}/intake")
    public ReceptionDtos.Episode intake(@PathVariable UUID id,@Valid @RequestBody ReceptionDtos.Intake request) { return service.intake(id,request); }
}
