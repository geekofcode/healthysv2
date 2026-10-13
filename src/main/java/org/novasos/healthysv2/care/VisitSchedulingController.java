package org.novasos.healthysv2.care;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/care/visits")
class VisitSchedulingController {
    private final VisitSchedulingService service;
    VisitSchedulingController(VisitSchedulingService service) { this.service=service; }
    @GetMapping("/{id}") VisitSchedulingDtos.Visit find(@PathVariable UUID id) { return service.find(id); }
    @PutMapping("/{id}/schedule") VisitSchedulingDtos.Visit program(@PathVariable UUID id,
            @Valid @RequestBody VisitSchedulingDtos.Program request) { return service.program(id,request); }
}
