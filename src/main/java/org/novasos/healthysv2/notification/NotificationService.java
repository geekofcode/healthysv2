package org.novasos.healthysv2.notification;

import static org.novasos.healthysv2.notification.api.NotificationDtos.*;

import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.novasos.healthysv2.patient.CurrentUserContext;
import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;
import org.novasos.healthysv2.shared.api.error.ResourceNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class NotificationService {
    private final JdbcTemplate jdbc;
    private final NotificationRepository repository;
    private final CurrentUserContext users;
    private final SimpMessagingTemplate broker;

    NotificationService(
            JdbcTemplate jdbc,
            NotificationRepository repository,
            CurrentUserContext users,
            SimpMessagingTemplate broker) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.users = users;
        this.broker = broker;
    }

    public List<NotificationResponse> create(CreateNotificationRequest request) {
        UUID actor = requireCurrentPerson();
        String type = normalize(request.type());
        String priority = request.priority() == null ? "NORMAL" : normalize(request.priority());
        if (!type.matches("[A-Z0-9_]{2,80}")) {
            throw rule("INVALID_NOTIFICATION_TYPE", "error.notification.type");
        }
        if (request.expiresAt() != null && !request.expiresAt().isAfter(Instant.now())) {
            throw rule("INVALID_NOTIFICATION_EXPIRATION", "error.notification.expiration");
        }
        request.recipientPersonIds().forEach(this::requirePerson);

        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into notification.notification(id,type,title,body,resource_type,resource_id,action_url,priority,expires_at,status) values (?,?,?,?,?,?,?,?,?,'CREATED')",
                id,
                type,
                blank(request.title()),
                request.body().trim(),
                blank(request.resourceType()),
                request.resourceId(),
                blank(request.actionUrl()),
                priority,
                timestamp(request.expiresAt()));
        for (UUID recipient : request.recipientPersonIds()) {
            jdbc.update(
                    "insert into notification.notification_recipient(notification_id,person_id) values (?,?)",
                    id,
                    recipient);
            jdbc.update(
                    "insert into notification.notification_delivery(notification_id,person_id,channel,provider,sent_at,delivered_at,status) select ?,?,'IN_APP','HEALTHYS',now(),now(),'DELIVERED' where coalesce((select in_app_enabled from notification.notification_preference where person_id=?),true)",
                    id,
                    recipient,
                    recipient);
        }
        audit(actor, id, "CREATE_NOTIFICATION");

        return request.recipientPersonIds().stream().map(recipient -> {
            NotificationResponse response = repository.findForRecipient(id, recipient).orElseThrow();
            broker.convertAndSend("/topic/notifications/" + recipient, response);
            return response;
        }).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> list(boolean unreadOnly, String type, Pageable pageable) {
        return repository.findForRecipient(requireCurrentPerson(), unreadOnly, type, pageable);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount() {
        return new UnreadCountResponse(repository.unreadCount(requireCurrentPerson()));
    }

    public NotificationResponse markRead(UUID notificationId) {
        UUID person = requireCurrentPerson();
        int changed = jdbc.update(
                "update notification.notification_recipient set read_at=coalesce(read_at,now()) where notification_id=? and person_id=?",
                notificationId,
                person);
        if (changed == 0) {
            throw new ResourceNotFoundException("Notification", notificationId);
        }
        audit(person, notificationId, "READ_NOTIFICATION");
        return repository.findForRecipient(notificationId, person).orElseThrow();
    }

    public MarkAllReadResponse markAllRead() {
        UUID person = requireCurrentPerson();
        int changed = jdbc.update(
                "update notification.notification_recipient nr set read_at=now() from notification.notification n where nr.notification_id=n.id and nr.person_id=? and nr.read_at is null and (n.expires_at is null or n.expires_at>now())",
                person);
        audit(person, person, "READ_ALL_NOTIFICATIONS");
        return new MarkAllReadResponse(changed);
    }

    @Transactional(readOnly = true)
    public NotificationPreferencesResponse preferences() {
        return repository.preferences(requireCurrentPerson());
    }

    public NotificationPreferencesResponse updatePreferences(UpdateNotificationPreferencesRequest request) {
        UUID person = requireCurrentPerson();
        if (!request.hasConsistentQuietHours()) {
            throw rule("INVALID_QUIET_HOURS", "error.notification.quiet-hours");
        }
        jdbc.update(
                "insert into notification.notification_preference(person_id,in_app_enabled,email_enabled,sms_enabled,push_enabled,quiet_hours_start,quiet_hours_end,locale,updated_at) values (?,?,?,?,?,?,?,?,now()) on conflict(person_id) do update set in_app_enabled=excluded.in_app_enabled,email_enabled=excluded.email_enabled,sms_enabled=excluded.sms_enabled,push_enabled=excluded.push_enabled,quiet_hours_start=excluded.quiet_hours_start,quiet_hours_end=excluded.quiet_hours_end,locale=excluded.locale,updated_at=now()",
                person,
                request.inAppEnabled(),
                request.emailEnabled(),
                request.smsEnabled(),
                request.pushEnabled(),
                request.quietHoursStart() == null ? null : Time.valueOf(request.quietHoursStart()),
                request.quietHoursEnd() == null ? null : Time.valueOf(request.quietHoursEnd()),
                request.locale());
        audit(person, person, "UPDATE_NOTIFICATION_PREFERENCES");
        return repository.preferences(person);
    }

    public boolean maySubscribe(Authentication authentication, UUID targetPerson) {
        try {
            return Objects.equals(personId(authentication), targetPerson);
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private UUID personId(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwt)) {
            throw new AccessDeniedException("JWT_REQUIRED");
        }
        UUID subject;
        try {
            subject = UUID.fromString(jwt.getToken().getSubject());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("INVALID_SUBJECT");
        }
        UUID person = jdbc.query(
                "select id from identity.person where keycloak_user_id=? or id=? limit 1",
                rs -> rs.next() ? (UUID) rs.getObject(1) : null,
                subject,
                subject);
        if (person == null) {
            throw new AccessDeniedException("PERSON_CONTEXT_MISSING");
        }
        return person;
    }

    private UUID requireCurrentPerson() {
        UUID person = users.current().personId();
        if (person == null) {
            throw new AccessDeniedException("PERSON_CONTEXT_MISSING");
        }
        return person;
    }

    private void requirePerson(UUID person) {
        Integer count = jdbc.queryForObject(
                "select count(*) from identity.person where id=? and status='ACTIVE'",
                Integer.class,
                person);
        if (count == null || count == 0) {
            throw new ResourceNotFoundException("Person", person);
        }
    }

    private void audit(UUID actor, UUID entity, String action) {
        jdbc.update(
                "insert into audit.audit_log(actor_person_id,module,entity_type,entity_id,action) values (?,'NOTIFICATION','Notification',?,?)",
                actor,
                entity,
                action);
    }

    private Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BusinessRuleException rule(String code, String messageKey) {
        return new BusinessRuleException(code, messageKey);
    }
}
