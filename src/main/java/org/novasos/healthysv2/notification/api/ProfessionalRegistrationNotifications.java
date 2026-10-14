package org.novasos.healthysv2.notification.api;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional, durable in-app announcements for the applicant’s own establishment administrators.
 */
@Service
@Transactional
public class ProfessionalRegistrationNotifications {
  private final JdbcTemplate jdbc;

  public ProfessionalRegistrationNotifications(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void professionalRegistrationSubmitted(UUID dossierId, UUID applicantPerson) {
    // The dossier ID is the idempotency key. No identity/document data goes into push payloads.
    jdbc.update(
        """
        insert into notification.notification(id,type,title,body,resource_type,resource_id,action_url,priority,status)
        values (?,'PROFESSIONAL_REGISTRATION_SUBMITTED','Inscription professionnelle à valider',
            'Un dossier professionnel complet attend votre validation.',
            'ProfessionalRegistration',?,'/employees','HIGH','CREATED')
        on conflict(id) do nothing
        """,
        dossierId,
        dossierId);
    jdbc.update(
        """
        insert into notification.notification_recipient(notification_id,person_id)
        select distinct ?, administrators.person_id from licensing.member applicant
        join licensing.member administrators on administrators.organization_id=applicant.organization_id
            and administrators.role='HOSPITAL_ADMIN' and administrators.status='ACTIVE'
        where applicant.person_id=? and applicant.status='ACTIVE'
        on conflict(notification_id,person_id) do nothing
        """,
        dossierId,
        applicantPerson);
  }
}
