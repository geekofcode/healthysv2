package org.novasos.healthysv2.lifecycle;

import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/data-deletion")
@PreAuthorize("isAuthenticated()")
class DataDeletionController {
  private final DataDeletionService service;

  DataDeletionController(DataDeletionService service) {
    this.service = service;
  }

  @PostMapping
  DeletionResponse request(@RequestHeader("Idempotency-Key") String key) {
    return service.request(key);
  }

  @GetMapping
  List<DeletionResponse> list() {
    return service.list();
  }

  @GetMapping("/{id}")
  DeletionResponse find(@PathVariable UUID id) {
    return service.find(id);
  }
}

record DeletionResponse(
    UUID id,
    String status,
    java.time.OffsetDateTime createdAt,
    java.time.OffsetDateTime completedAt,
    int removedCount,
    int anonymizedCount,
    int retainedCount,
    String notes) {}
