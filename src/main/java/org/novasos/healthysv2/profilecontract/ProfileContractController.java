package org.novasos.healthysv2.profilecontract;

import jakarta.validation.Valid;
import java.util.*;
import org.novasos.healthysv2.profilecontract.api.ContractDtos.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/profile-contracts")
class ProfileContractController {
  private final ProfileContractService service;

  ProfileContractController(ProfileContractService service) {
    this.service = service;
  }

  @GetMapping("/{profileId}/template")
  Template template(@PathVariable UUID profileId) {
    return service.currentTemplate(profileId);
  }

  @GetMapping("/{profileId}")
  State state(@PathVariable UUID profileId) {
    return service.state(profileId);
  }

  @PostMapping("/{profileId}/sign")
  State sign(@PathVariable UUID profileId, @Valid @RequestBody SignRequest body) {
    return service.sign(profileId, body);
  }

  @GetMapping("/{profileId}/pdf")
  ResponseEntity<byte[]> pdf(@PathVariable UUID profileId) {
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_PDF)
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header("X-Content-Type-Options", "nosniff")
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"contrat-healthys.pdf\"")
        .body(service.pdf(profileId));
  }
}
