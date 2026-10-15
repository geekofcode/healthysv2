package org.novasos.healthysv2.patienthealth;

import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
class PatientPhotoController {
  private final PatientPhotoService photos;

  PatientPhotoController(PatientPhotoService photos) {
    this.photos = photos;
  }

  @GetMapping("/api/v1/patients/me/health/photo")
  ResponseEntity<byte[]> self() {
    return response(photos.self());
  }

  @PutMapping(
      value = "/api/v1/patients/me/health/photo",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  void upload(@RequestParam MultipartFile file) {
    photos.put(file);
  }

  @GetMapping("/api/v1/patients/{patientId}/health/photo")
  ResponseEntity<byte[]> clinical(@PathVariable UUID patientId) {
    return response(photos.clinical(patientId));
  }

  private ResponseEntity<byte[]> response(byte[] image) {
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .header(HttpHeaders.CACHE_CONTROL, "no-store")
        .header("X-Content-Type-Options", "nosniff")
        .body(image);
  }
}
