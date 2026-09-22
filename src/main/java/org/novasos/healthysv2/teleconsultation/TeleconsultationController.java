package org.novasos.healthysv2.teleconsultation;

import static org.novasos.healthysv2.teleconsultation.api.TeleconsultationDtos.*;
import java.net.*;import java.util.*;import jakarta.validation.Valid;import io.swagger.v3.oas.annotations.tags.Tag;import org.novasos.healthysv2.shared.api.ApiPaths;import org.springframework.http.*;import org.springframework.security.access.prepost.PreAuthorize;import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping(ApiPaths.V1+"/video-sessions") @Tag(name="Teleconsultation",description="LiveKit video sessions and patient waiting room") @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','DOCTOR','NURSE','PATIENT')")
class TeleconsultationController {
    private final TeleconsultationService service;TeleconsultationController(TeleconsultationService service){this.service=service;}
    @PostMapping @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','DOCTOR','NURSE')") ResponseEntity<VideoSessionResponse>create(@Valid@RequestBody CreateVideoSessionRequest request){var out=service.create(request);return ResponseEntity.created(URI.create(ApiPaths.V1+"/video-sessions/"+out.id())).body(out);}
    @GetMapping List<VideoSessionResponse>list(){return service.list();}@GetMapping("/{id}")VideoSessionResponse find(@PathVariable UUID id){return service.find(id);}
    @PostMapping("/{id}/waiting-room")WaitingRoomResponse enter(@PathVariable UUID id){return service.enter(id);}
    @PostMapping("/{id}/waiting-room/{entry}/admit")WaitingRoomResponse admit(@PathVariable UUID id,@PathVariable UUID entry){return service.admit(id,entry);}
    @PostMapping("/{id}/token")JoinTokenResponse token(@PathVariable UUID id){return service.token(id);}
    @PostMapping("/{id}/start")VideoSessionResponse start(@PathVariable UUID id){return service.start(id);}
    @PostMapping("/{id}/complete")VideoSessionResponse complete(@PathVariable UUID id){return service.complete(id);}
    @PostMapping("/{id}/leave")@ResponseStatus(HttpStatus.NO_CONTENT)void leave(@PathVariable UUID id){service.leave(id);}
}
