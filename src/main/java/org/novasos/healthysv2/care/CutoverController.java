package org.novasos.healthysv2.care;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/care/cutover")
class CutoverController {
  private final CutoverService service;

  CutoverController(CutoverService service) {
    this.service = service;
  }

  @GetMapping
  CutoverService.State read() {
    return service.read();
  }

  record Activate(@NotNull UUID migrationRunId) {}

  @PostMapping("/activate")
  @PreAuthorize("hasRole('PLATFORM_ADMIN')")
  CutoverService.State activate(@Valid @RequestBody Activate request) {
    return service.activate(request.migrationRunId());
  }
}
