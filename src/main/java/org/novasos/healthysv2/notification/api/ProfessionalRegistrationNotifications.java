package org.novasos.healthysv2.notification.api;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional, durable in-app announcements for verified platform administrators. */
@Service
@Transactional
public class ProfessionalRegistrationNotifications {
    private final JdbcTemplate jdbc;

    public ProfessionalRegistrationNotifications(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void professionalRegistrationSubmitted(UUID dossierId, UUID applicantPerson) {
        // The dossier ID is the idempotency key. No identity/document data goes into push payloads.
        jdbc.update("""
            insert into notification.notification(id,type,title,body,resource_type,resource_id,action_url,priority,status)
            values (?,'PROFESSIONAL_REGISTRATION_SUBMITTED','Inscription professionnelle à valider',
                'Un dossier professionnel complet attend votre validation.',
                'ProfessionalRegistration',?,'/admin/professional-requests','HIGH','CREATED')
            on conflict(id) do nothing
            """, dossierId, dossierId);
        jdbc.update("""
            insert into notification.notification_audience(notification_id,required_role)
            values (?,'ROLE_PLATFORM_ADMIN') on conflict(notification_id) do nothing
            """, dossierId);
    }
}
