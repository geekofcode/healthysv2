package org.novasos.healthysv2.notification;

import static org.novasos.healthysv2.notification.api.NotificationDtos.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.novasos.healthysv2.shared.api.ApiPaths;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ApiPaths.V1 + "/notifications")
@Tag(name = "Notifications", description = "In-app notifications, unread state and delivery preferences")
@PreAuthorize("isAuthenticated()")
class NotificationController {
    private final NotificationService service;

    NotificationController(NotificationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create and publish a notification")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','HOSPITAL_AGENT','DOCTOR','NURSE','PHARMACIST','LAB_TECHNICIAN')")
    ResponseEntity<List<NotificationResponse>> create(@Valid @RequestBody CreateNotificationRequest request) {
        List<NotificationResponse> response = service.create(request);
        return ResponseEntity.created(URI.create(ApiPaths.V1 + "/notifications/" + response.getFirst().id())).body(response);
    }

    @GetMapping
    @Operation(summary = "List notifications for the authenticated person")
    PageResponse<NotificationResponse> list(
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(required = false) String type,
            Pageable pageable) {
        return service.list(unreadOnly, type, pageable);
    }

    @GetMapping("/unread-count")
    UnreadCountResponse unreadCount() {
        return service.unreadCount();
    }

    @PatchMapping("/{id}/read")
    NotificationResponse markRead(@PathVariable UUID id) {
        return service.markRead(id);
    }

    @PostMapping("/read-all")
    MarkAllReadResponse markAllRead() {
        return service.markAllRead();
    }

    @GetMapping("/preferences")
    NotificationPreferencesResponse preferences() {
        return service.preferences();
    }

    @PutMapping("/preferences")
    NotificationPreferencesResponse updatePreferences(
            @Valid @RequestBody UpdateNotificationPreferencesRequest request) {
        return service.updatePreferences(request);
    }
}
