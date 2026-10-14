package org.novasos.healthysv2.identity;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/** Durable identity provisioning only. Organization permissions remain in HEALTH'YS. */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "STAFF_ACCOUNT_PROVISIONING_ENABLED", havingValue = "true")
class StaffAccountProvisioningWorker {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final RestClient client;
  private final String admin, realm, issuer, clientId, secret, webClient, redirect;

  StaffAccountProvisioningWorker(
      JdbcTemplate jdbc,
      PlatformTransactionManager manager,
      @Value("${KEYCLOAK_ADMIN_SERVER_URL:}") String admin,
      @Value("${KEYCLOAK_ADMIN_REALM:}") String realm,
      @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
      @Value("${KEYCLOAK_ADMIN_CLIENT_ID:}") String clientId,
      @Value("${KEYCLOAK_ADMIN_CLIENT_SECRET:}") String secret,
      @Value("${STAFF_ACCOUNT_WEB_CLIENT_ID:}") String webClient,
      @Value("${STAFF_ACCOUNT_REDIRECT_URI:}") String redirect) {
    if (admin.isBlank()
        || realm.isBlank()
        || clientId.isBlank()
        || secret.isBlank()
        || !"https".equals(URI.create(admin).getScheme())
        || !"https".equals(URI.create(issuer).getScheme()))
      throw new IllegalArgumentException(
          "Staff provisioning requires HTTPS Keycloak admin configuration");
    if (!redirect.isBlank()
        && (webClient.isBlank() || !"https".equals(URI.create(redirect).getScheme())))
      throw new IllegalArgumentException("Staff invitation redirect requires HTTPS and web client");
    this.jdbc = jdbc;
    transactions = new TransactionTemplate(manager);
    this.admin = admin.replaceAll("/+$", "");
    this.realm = realm;
    this.issuer = issuer;
    this.clientId = clientId;
    this.secret = secret;
    this.webClient = webClient;
    this.redirect = redirect;
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    factory.setReadTimeout(Duration.ofSeconds(10));
    client = RestClient.builder().requestFactory(factory).build();
  }

  @Scheduled(fixedDelayString = "${STAFF_ACCOUNT_PROVISIONING_POLL_MS:10000}")
  void poll() {
    for (int i = 0; i < 10; i++) {
      Work work = transactions.execute(tx -> claim());
      if (work == null) break;
      try {
        deliver(work);
      } catch (RemoteConflict exception) {
        fail(work, "BLOCKED", "KEYCLOAK_ACCOUNT_REQUIRES_RECONCILIATION");
      } catch (Exception exception) {
        fail(work, "FAILED", "KEYCLOAK_PROVISIONING_FAILED");
      }
    }
  }

  private Work claim() {
    var rows =
        jdbc.query(
            """
            select o.person_id,o.email,o.remote_user_id,p.first_name,p.last_name
            from identity.staff_account_outbox o join identity.person p on p.id=o.person_id
            where p.status='ACTIVE' and o.status in ('PENDING','FAILED','PROCESSING','ACCOUNT_CREATED')
              and (o.lease_until is null or o.lease_until<now()) and o.attempts<10
              and (o.attempts=0 or o.updated_at<now()-interval '30 seconds')
            order by o.updated_at limit 1 for update of o skip locked
            """,
            (row, index) ->
                new Work(
                    row.getObject(1, UUID.class),
                    row.getString(2),
                    row.getObject(3, UUID.class),
                    row.getString(4),
                    row.getString(5)));
    if (rows.isEmpty()) return null;
    Work work = rows.getFirst();
    jdbc.update(
        "update identity.staff_account_outbox set"
            + " status='PROCESSING',attempts=attempts+1,lease_until=now()+interval '5"
            + " minutes',updated_at=now() where person_id=?",
        work.personId());
    return work;
  }

  private void deliver(Work work) {
    String token = token();
    UUID subject = work.subject();
    if (subject == null) subject = ensureRemote(work, token);
    UUID linked = subject;
    transactions.executeWithoutResult(
        tx -> {
          var active =
              jdbc.queryForObject(
                  "select status='ACTIVE' from identity.person where id=? for update",
                  Boolean.class,
                  work.personId());
          if (!Boolean.TRUE.equals(active)) throw new RemoteConflict();
          jdbc.update(
              "update identity.staff_account_outbox set"
                  + " remote_user_id=?,status='ACCOUNT_CREATED',updated_at=now() where person_id=?",
              linked,
              work.personId());
          int count =
              jdbc.update(
                  "update identity.person set keycloak_user_id=?,updated_at=now(),version=version+1"
                      + " where id=? and (keycloak_user_id is null or keycloak_user_id=?)",
                  linked,
                  work.personId(),
                  linked);
          if (count != 1) throw new RemoteConflict();
        });
    // Keep the identity row locked while enabling/delivering so deletion cannot race it.
    transactions.executeWithoutResult(
        tx -> {
          Boolean active =
              jdbc.queryForObject(
                  "select status='ACTIVE' from identity.person where id=? for update",
                  Boolean.class,
                  work.personId());
          if (!Boolean.TRUE.equals(active)) throw new RemoteConflict();
          client
              .put()
              .uri(admin + "/admin/realms/{realm}/users/{user}", realm, linked)
              .headers(h -> h.setBearerAuth(token))
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("enabled", true))
              .retrieve()
              .toBodilessEntity();
          // A crash may resend the invitation; the correlation prevents a duplicate account.
          client
              .put()
              .uri(invitationUri(linked))
              .headers(h -> h.setBearerAuth(token))
              .contentType(MediaType.APPLICATION_JSON)
              .body(List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"))
              .retrieve()
              .toBodilessEntity();
          jdbc.update(
              "update identity.staff_account_outbox set"
                  + " status='DISPATCHED',lease_until=null,last_error=null,updated_at=now() where"
                  + " person_id=?",
              work.personId());
        });
  }

  private UUID ensureRemote(Work work, String token) {
    UUID existing = findOwned(work, token);
    if (existing != null) return existing;
    Map<String, Object> user =
        Map.of(
            "username",
            work.email(),
            "email",
            work.email(),
            "firstName",
            work.firstName(),
            "lastName",
            work.lastName(),
            "enabled",
            false,
            "emailVerified",
            false,
            "requiredActions",
            List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"),
            "attributes",
            Map.of("healthysProvisioningPersonId", List.of(work.personId().toString())));
    try {
      client
          .post()
          .uri(admin + "/admin/realms/{realm}/users", realm)
          .headers(h -> h.setBearerAuth(token))
          .contentType(MediaType.APPLICATION_JSON)
          .body(user)
          .retrieve()
          .toBodilessEntity();
    } catch (HttpClientErrorException.Conflict exception) {
      /* Reconcile only our correlation attribute. */
    }
    UUID created = findOwned(work, token);
    if (created == null) throw new IllegalStateException("Remote identity not observable");
    return created;
  }

  private UUID findOwned(Work work, String token) {
    List<?> users =
        client
            .get()
            .uri(
                admin + "/admin/realms/{realm}/users?email={email}&exact=true&max=2",
                realm,
                work.email())
            .headers(h -> h.setBearerAuth(token))
            .retrieve()
            .body(List.class);
    if (users == null || users.isEmpty()) return null;
    if (users.size() != 1
        || !(users.getFirst() instanceof Map<?, ?> user)
        || !owned(user, work.personId(), work.email())) throw new RemoteConflict();
    return UUID.fromString(user.get("id").toString());
  }

  static boolean owned(Map<?, ?> user, UUID person, String email) {
    if (!(user.get("email") instanceof String mail) || !email.equalsIgnoreCase(mail)) return false;
    if (!(user.get("attributes") instanceof Map<?, ?> attributes)) return false;
    Object correlation = attributes.get("healthysProvisioningPersonId");
    return correlation instanceof List<?> values
        && values.size() == 1
        && person.toString().equals(values.getFirst());
  }

  private URI invitationUri(UUID subject) {
    var uri =
        org.springframework.web.util.UriComponentsBuilder.fromUriString(
                admin + "/admin/realms/{realm}/users/{user}/execute-actions-email")
            .queryParam("lifespan", 86400);
    if (!webClient.isBlank()) uri.queryParam("client_id", webClient);
    if (!redirect.isBlank()) uri.queryParam("redirect_uri", redirect);
    return uri.buildAndExpand(realm, subject).encode().toUri();
  }

  private String token() {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", clientId);
    form.add("client_secret", secret);
    Map<?, ?> response =
        client
            .post()
            .uri(issuer + "/protocol/openid-connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Map.class);
    if (response == null || !(response.get("access_token") instanceof String token))
      throw new IllegalStateException("Missing token");
    return token;
  }

  private void fail(Work work, String status, String code) {
    jdbc.update(
        "update identity.staff_account_outbox set"
            + " status=?,last_error=?,lease_until=null,updated_at=now() where person_id=?",
        status,
        code,
        work.personId());
  }

  record Work(UUID personId, String email, UUID subject, String firstName, String lastName) {}

  private static final class RemoteConflict extends RuntimeException {}
}
