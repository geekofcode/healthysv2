package org.novasos.healthysv2.notification;

import static org.novasos.healthysv2.notification.api.NotificationDtos.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.novasos.healthysv2.shared.api.dto.PageResponse;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class NotificationRepository {
    private final JdbcTemplate jdbc;

    NotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    PageResponse<NotificationResponse> findForRecipient(
            UUID personId,
            boolean unreadOnly,
            String type,
            Pageable pageable) {
        StringBuilder where = new StringBuilder(" where nr.person_id=? and (n.expires_at is null or n.expires_at>now())");
        List<Object> parameters = new ArrayList<>();
        parameters.add(personId);
        if (unreadOnly) {
            where.append(" and nr.read_at is null");
        }
        if (type != null && !type.isBlank()) {
            where.append(" and n.type=?");
            parameters.add(type.trim().toUpperCase());
        }

        String select = "select n.id,n.type,n.title,n.body,n.resource_type,n.resource_id,n.action_url,n.priority,n.created_at,n.expires_at,n.status,nr.read_at from notification.notification n join notification.notification_recipient nr on nr.notification_id=n.id";
        List<Object> pageParameters = new ArrayList<>(parameters);
        pageParameters.add(pageable.getPageSize());
        pageParameters.add(pageable.getOffset());
        List<NotificationResponse> content = jdbc.query(
                select + where + " order by n.created_at desc limit ? offset ?",
                (rs, row) -> map(rs),
                pageParameters.toArray());
        Long total = jdbc.queryForObject(
                "select count(*) from notification.notification n join notification.notification_recipient nr on nr.notification_id=n.id" + where,
                Long.class,
                parameters.toArray());
        return PageResponse.from(new PageImpl<>(content, pageable, total == null ? 0 : total));
    }

    Optional<NotificationResponse> findForRecipient(UUID notificationId, UUID personId) {
        return jdbc.query(
                "select n.id,n.type,n.title,n.body,n.resource_type,n.resource_id,n.action_url,n.priority,n.created_at,n.expires_at,n.status,nr.read_at from notification.notification n join notification.notification_recipient nr on nr.notification_id=n.id where n.id=? and nr.person_id=?",
                (rs, row) -> map(rs), notificationId, personId).stream().findFirst();
    }

    long unreadCount(UUID personId) {
        Long value = jdbc.queryForObject(
                "select count(*) from notification.notification_recipient nr join notification.notification n on n.id=nr.notification_id where nr.person_id=? and nr.read_at is null and (n.expires_at is null or n.expires_at>now())",
                Long.class,
                personId);
        return value == null ? 0 : value;
    }

    NotificationPreferencesResponse preferences(UUID personId) {
        return jdbc.query(
                "select in_app_enabled,email_enabled,sms_enabled,push_enabled,quiet_hours_start,quiet_hours_end,locale,updated_at from notification.notification_preference where person_id=?",
                rs -> rs.next()
                        ? new NotificationPreferencesResponse(
                                rs.getBoolean(1), rs.getBoolean(2), rs.getBoolean(3), rs.getBoolean(4),
                                rs.getTime(5) == null ? null : rs.getTime(5).toLocalTime(),
                                rs.getTime(6) == null ? null : rs.getTime(6).toLocalTime(),
                                rs.getString(7), rs.getTimestamp(8).toInstant())
                        : new NotificationPreferencesResponse(true, false, false, false, null, null, "en", Instant.EPOCH),
                personId);
    }

    private NotificationResponse map(ResultSet rs) throws SQLException {
        Timestamp expires = rs.getTimestamp(10);
        Timestamp read = rs.getTimestamp(12);
        return new NotificationResponse(
                (UUID) rs.getObject(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), (UUID) rs.getObject(6), rs.getString(7), rs.getString(8),
                rs.getTimestamp(9).toInstant(), expires == null ? null : expires.toInstant(),
                rs.getString(11), read == null ? null : read.toInstant(), read != null);
    }
}
