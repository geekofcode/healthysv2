package org.novasos.healthysv2.lifecycle;

import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class DataDeletionService {
  private final JdbcTemplate jdbc;

  DataDeletionService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  UUID owner() {
    if (!(SecurityContextHolder.getContext().getAuthentication()
        instanceof JwtAuthenticationToken jwt))
      throw new AccessDeniedException("AUTHENTICATION_REQUIRED");
    UUID subject;
    try {
      subject = UUID.fromString(jwt.getToken().getSubject());
    } catch (IllegalArgumentException ex) {
      throw new AccessDeniedException("INVALID_SUBJECT");
    }
    var people =
        jdbc.queryForList(
            "select id from identity.person where keycloak_user_id=?", UUID.class, subject);
    if (people.size() != 1) throw new AccessDeniedException("PERSON_REQUIRED");
    return people.getFirst();
  }

  @Transactional
  DeletionResponse request(String key) {
    validateKey(key);
    UUID person = owner();
    jdbc.queryForObject("select id from identity.person where id=? for update", UUID.class, person);
    var existing =
        query(
            "select * from lifecycle.deletion_request where person_id=? and idempotency_key=?",
            person,
            key);
    if (!existing.isEmpty()) return existing.getFirst();
    Integer pending =
        jdbc.queryForObject(
            "select count(*) from lifecycle.deletion_request where person_id=? and status in"
                + " ('QUEUED','RUNNING')",
            Integer.class,
            person);
    if (pending != null && pending > 0)
      throw new ResponseStatusException(HttpStatus.CONFLICT, "DELETION_ALREADY_PENDING");
    UUID id = UUID.randomUUID();
    jdbc.update(
        "insert into lifecycle.deletion_request(id,person_id,idempotency_key) values(?,?,?)",
        id,
        person,
        key);
    return findOwned(id, person);
  }

  static void validateKey(String key) {
    if (key == null || key.isBlank() || key.length() > 128 || !key.matches("[A-Za-z0-9._:-]+"))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY");
  }

  List<DeletionResponse> list() {
    return query(
        "select * from lifecycle.deletion_request where person_id=? order by created_at desc limit"
            + " 100",
        owner());
  }

  DeletionResponse find(UUID id) {
    return findOwned(id, owner());
  }

  private DeletionResponse findOwned(UUID id, UUID person) {
    var rows =
        query("select * from lifecycle.deletion_request where id=? and person_id=?", id, person);
    if (rows.isEmpty())
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "DELETION_REQUEST_NOT_FOUND");
    return rows.getFirst();
  }

  private List<DeletionResponse> query(String sql, Object... args) {
    return jdbc.query(
        sql,
        (rs, n) ->
            new DeletionResponse(
                rs.getObject("id", UUID.class),
                rs.getString("status"),
                rs.getObject("created_at", java.time.OffsetDateTime.class),
                rs.getObject("completed_at", java.time.OffsetDateTime.class),
                rs.getInt("removed_count"),
                rs.getInt("anonymized_count"),
                rs.getInt("retained_count"),
                rs.getString("notes")),
        args);
  }
}
