package org.novasos.healthysv2.profilecontract;

import jakarta.validation.Valid;
import java.util.*;
import org.novasos.healthysv2.profilecontract.api.ContractDtos.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/contract-templates")
class ContractTemplateController {
  private final ProfileContractService service;

  ContractTemplateController(ProfileContractService service) {
    this.service = service;
  }

  @GetMapping
  List<Template> list() {
    return service.templates();
  }

  @PostMapping
  Template create(@Valid @RequestBody TemplateRequest body) {
    return service.createTemplate(body);
  }

  @PutMapping("/{id}")
  Template update(@PathVariable UUID id, @Valid @RequestBody TemplateRequest body) {
    return service.updateTemplate(id, body);
  }

  @PostMapping("/{id}/publish")
  Template publish(@PathVariable UUID id) {
    return service.publishTemplate(id);
  }
}
