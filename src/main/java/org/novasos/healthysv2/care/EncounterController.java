package org.novasos.healthysv2.care;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/care/visits/{visitId}")
public class EncounterController {
    private final EncounterService service;
    EncounterController(EncounterService service) { this.service=service; }
    @PostMapping("/start")
    public EncounterDtos.Started start(@PathVariable UUID visitId,@Valid @RequestBody EncounterDtos.Version request) {
        return service.start(visitId,request.version());
    }
    @GetMapping("/report")
    public EncounterDtos.Report report(@PathVariable UUID visitId) { return service.read(visitId); }
    @PutMapping("/report")
    public EncounterDtos.Report save(@PathVariable UUID visitId,@Valid @RequestBody EncounterDtos.Draft request) {
        return service.save(visitId,request);
    }
    @PostMapping("/report/finalize")
    public EncounterDtos.Report finalizeReport(@PathVariable UUID visitId,@Valid @RequestBody EncounterDtos.Version request) {
        return service.finalizeReport(visitId,request.version());
    }
    @PostMapping("/report/corrections")
    public EncounterDtos.Report correct(@PathVariable UUID visitId,@Valid @RequestBody EncounterDtos.Correction request) {
        return service.correct(visitId,request);
    }
}
