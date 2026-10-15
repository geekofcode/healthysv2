package org.novasos.healthysv2.identity;

import java.util.*;
import org.novasos.healthysv2.shared.api.error.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
class AccountResetService {
  private final JdbcTemplate db;

  AccountResetService(JdbcTemplate db) {
    this.db = db;
  }

  record Request(UUID requestId, String status) {}

  private UUID administrator() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (!(auth instanceof JwtAuthenticationToken jwt)
        || jwt.getAuthorities().stream()
            .noneMatch(a -> a.getAuthority().equals("ROLE_PLATFORM_ADMIN")))
      throw new AccessDeniedException("SELECTED_PLATFORM_ADMIN_REQUIRED");
    UUID subject;
    try {
      subject = UUID.fromString(jwt.getToken().getSubject());
    } catch (IllegalArgumentException e) {
      throw new AccessDeniedException("ACCOUNT_REQUIRED");
    }
    var ids =
        db.queryForList(
            "select p.id from identity.person p join accessprofile.platform_administrator a on"
                + " a.person_id=p.id and a.status='ACTIVE' where p.keycloak_user_id=? and"
                + " p.status='ACTIVE'",
            UUID.class,
            subject);
    if (ids.size() != 1) throw new AccessDeniedException("ACTIVE_PLATFORM_ADMIN_REQUIRED");
    return ids.getFirst();
  }

  Request enqueue(UUID person, String key) {
    UUID actor = administrator();
    if (key == null || !key.matches("[A-Za-z0-9._:-]{1,128}"))
      throw new IllegalArgumentException("INVALID_IDEMPOTENCY_KEY");
    var accounts =
        db.queryForList(
            "select keycloak_user_id from identity.person where id=? and status='ACTIVE' and"
                + " keycloak_user_id is not null for update",
            UUID.class,
            person);
    if (accounts.size() != 1)
      throw new ConflictException(
          "ACCOUNT_NOT_LINKED", "The account must be provisioned before reset");
    UUID id = UUID.randomUUID();
    db.update(
        "insert into identity.account_reset_outbox(id,person_id,actor_person_id,idempotency_key)"
            + " values(?,?,?,?) on conflict(person_id,actor_person_id,idempotency_key) do nothing",
        id,
        person,
        actor,
        key);
    var response =
        db.query(
                "select id,status from identity.account_reset_outbox where person_id=? and"
                    + " actor_person_id=? and idempotency_key=?",
                (r, n) -> new Request((UUID) r.getObject(1), r.getString(2)),
                person,
                actor,
                key)
            .getFirst();
    db.update(
        "insert into audit.audit_log(actor_person_id,module,entity_type,entity_id,action,new_value)"
            + " values (?,'PLATFORM_ACCOUNT','AccountReset',?,'RESET_REQUESTED',?::jsonb)",
        actor,
        response.requestId(),
        "{\"personId\":\"" + person + "\"}");
    return response;
  }

  @Transactional(readOnly = true)
  Request state(UUID person, UUID request) {
    administrator();
    var rows =
        db.query(
            "select id,status from identity.account_reset_outbox where id=? and person_id=?",
            (r, n) -> new Request((UUID) r.getObject(1), r.getString(2)),
            request,
            person);
    if (rows.isEmpty()) throw new ResourceNotFoundException("AccountReset", request);
    return rows.getFirst();
  }
}
