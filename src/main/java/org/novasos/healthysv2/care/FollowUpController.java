package org.novasos.healthysv2.care;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/care/episodes")
class FollowUpController {
    private final FollowUpService service;
    FollowUpController(FollowUpService service) {this.service=service;}
    record Request(@NotNull UUID professionalId,@NotNull Instant scheduledAt,@Size(max=4000) String reason) {}
    @PostMapping("/{id}/follow-ups") @ResponseStatus(HttpStatus.CREATED)
    VisitSchedulingDtos.Visit create(@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Request request) {
        return service.create(id,key,request);
    }
}
