package org.novasos.healthysv2.notification;

import java.util.UUID;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications/devices")
@PreAuthorize("isAuthenticated()")
class PushDeviceController {
    private final PushDevices devices;
    PushDeviceController(PushDevices devices) {this.devices=devices;}
    @PostMapping("/revoke")
    @PreAuthorize("permitAll()")
    ResponseEntity<Void> revokeCapability(@Valid @RequestBody PushDevices.RevocationRequest request) {devices.revokeCapability(request);return ResponseEntity.noContent().build();}
    @PutMapping("/{installationId}")
    PushDevices.DeviceResponse register(@PathVariable UUID installationId,@Valid @RequestBody PushDevices.DeviceRequest request) {return devices.register(installationId,request);}
    @DeleteMapping("/{installationId}")
    ResponseEntity<Void> revoke(@PathVariable UUID installationId) {devices.revoke(installationId);return ResponseEntity.noContent().build();}
}
