package org.novasos.healthysv2.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.novasos.healthysv2.TestcontainersConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class NotificationRepositoryIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationRepository repository;

    @Test
    void filtersUnreadAndExpiredNotifications() {
        UUID person = person();
        UUID active = notification("ACTIVE", false);
        UUID expired = notification("EXPIRED", true);
        jdbc.update("insert into notification.notification_recipient(notification_id,person_id) values (?,?)", active, person);
        jdbc.update("insert into notification.notification_recipient(notification_id,person_id) values (?,?)", expired, person);

        var page = repository.findForRecipient(person, true, null, PageRequest.of(0, 20));

        assertThat(page.content()).extracting("id").containsExactly(active);
        assertThat(repository.unreadCount(person)).isEqualTo(1);
    }

    @Test
    void returnsDefaultPreferences() {
        var preferences = repository.preferences(person());

        assertThat(preferences.inAppEnabled()).isTrue();
        assertThat(preferences.emailEnabled()).isFalse();
        assertThat(preferences.locale()).isEqualTo("en");
    }

    private UUID person() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into identity.person(id,person_number,first_name,last_name,status) values (?,?, 'Notification','User','ACTIVE')",
                id,
                "PER-" + id);
        return id;
    }

    private UUID notification(String type, boolean expired) {
        UUID id = UUID.randomUUID();
        if (!expired) {
            jdbc.update(
                    "insert into notification.notification(id,type,body) values (?,?,?)",
                    id, type, type);
        } else {
            jdbc.update(
                    "insert into notification.notification(id,type,body,created_at,expires_at) values (?,?,?,now() - interval '2 hours',now() - interval '1 hour')",
                    id, type, type);
        }
        return id;
    }
}
