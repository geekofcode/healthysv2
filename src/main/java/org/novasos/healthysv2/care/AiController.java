package org.novasos.healthysv2.care;

import jakarta.validation.Valid;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/care/visits/{visitId}/ai")
class AiController {
  private final AiJobService service;

  AiController(AiJobService service) {
    this.service = service;
  }

  @GetMapping("/jobs")
  List<AiDtos.Job> list(@PathVariable UUID visitId) {
    return service.list(visitId);
  }

  @GetMapping("/jobs/{jobId}")
  AiDtos.Job get(@PathVariable UUID visitId, @PathVariable UUID jobId) {
    return service.get(visitId, jobId);
  }

  @PostMapping("/jobs")
  AiDtos.Job create(
      @PathVariable UUID visitId,
      @RequestHeader("Idempotency-Key") String key,
      @Valid @RequestBody AiDtos.Request request) {
    return service.create(visitId, key, request, null);
  }

  @PostMapping(value = "/dictation", consumes = "multipart/form-data")
  AiDtos.Job dictate(
      @PathVariable UUID visitId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestPart("audio") MultipartFile audio) {
    return service.create(visitId, key, new AiDtos.Request(AiDtos.Kind.TRANSCRIPTION, null), audio);
  }
}
