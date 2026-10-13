package org.novasos.healthysv2.care;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/care")
public class PhysicianQueueController {
    private final PhysicianQueueService service;
    PhysicianQueueController(PhysicianQueueService service) { this.service=service; }
    @GetMapping("/calendar")
    public PhysicianQueueDtos.Calendar calendar(@RequestParam LocalDate date,
            @RequestParam(required=false) UUID professionalId) { return service.calendar(professionalId,date); }
    @PostMapping("/queue/presence")
    public PhysicianQueueDtos.Calendar presence(@Valid @RequestBody PhysicianQueueDtos.Presence request) { return service.presence(request); }
    @PostMapping("/queue/next")
    public PhysicianQueueDtos.Calendar next(@Valid @RequestBody PhysicianQueueDtos.Next request) { return service.next(request); }
    @PutMapping("/queue/order")
    public PhysicianQueueDtos.Calendar order(@Valid @RequestBody PhysicianQueueDtos.Order request) { return service.order(request); }
}
