package org.novasos.healthysv2.identity;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "ACCOUNT_RESET_ENABLED", havingValue = "true")
class AccountResetWorker {
  private final JdbcTemplate db;
  private final TransactionTemplate tx;
  private final RestClient client;
  private final String admin, realm, issuer, clientId, secret, webClient, redirect;

  AccountResetWorker(
      JdbcTemplate db,
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
        || !"https".equals(URI.create(issuer).getScheme())
        || (!redirect.isBlank()
            && (webClient.isBlank() || !"https".equals(URI.create(redirect).getScheme()))))
      throw new IllegalArgumentException(
          "Account reset requires HTTPS Keycloak service configuration");
    this.db = db;
    this.tx = new TransactionTemplate(manager);
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

  private record Work(UUID id, UUID person, UUID subject, UUID actor, UUID lease) {}

  @Scheduled(fixedDelayString = "${ACCOUNT_RESET_POLL_MS:10000}")
  void poll() {
    for (int i = 0; i < 10; i++) {
      Work work = tx.execute(s -> claim());
      if (work == null) return;
      try {
        deliver(work);
      } catch (Exception e) {
        tx.executeWithoutResult(
            s ->
                db.update(
                    "update identity.account_reset_outbox set"
                        + " status='FAILED',error_code='KEYCLOAK_RESET_FAILED',lease_token=null,lease_until=null,updated_at=clock_timestamp()"
                        + " where id=? and lease_token=?",
                    work.id(),
                    work.lease()));
      }
    }
  }

  private Work claim() {
    var rows =
        db.queryForList(
            "select r.id,r.person_id,r.actor_person_id,p.keycloak_user_id from"
                + " identity.account_reset_outbox r join identity.person p on p.id=r.person_id and"
                + " p.status='ACTIVE' join accessprofile.platform_administrator a on"
                + " a.person_id=r.actor_person_id and a.status='ACTIVE' where p.keycloak_user_id is"
                + " not null and r.status in ('QUEUED','FAILED','RUNNING') and r.attempts<5 and"
                + " (r.lease_until is null or r.lease_until<clock_timestamp()) and (r.attempts=0 or"
                + " r.updated_at<clock_timestamp()-interval '30 seconds') order by r.created_at for"
                + " update of r skip locked limit 1");
    if (rows.isEmpty()) return null;
    var r = rows.getFirst();
    UUID lease = UUID.randomUUID();
    db.update(
        "update identity.account_reset_outbox set"
            + " status='RUNNING',attempts=attempts+1,lease_token=?,lease_until=clock_timestamp()+interval"
            + " '2 minutes',updated_at=clock_timestamp() where id=?",
        lease,
        r.get("id"));
    return new Work(
        (UUID) r.get("id"),
        (UUID) r.get("person_id"),
        (UUID) r.get("keycloak_user_id"),
        (UUID) r.get("actor_person_id"),
        lease);
  }

  private void deliver(Work w) {
    String token = token();
    tx.executeWithoutResult(
        s -> {
          var target =
              db.queryForList(
                  "select keycloak_user_id from identity.person where id=? and status='ACTIVE' for"
                      + " update",
                  UUID.class,
                  w.person());
          Integer administrator =
              db.queryForObject(
                  "select count(*) from accessprofile.platform_administrator a join identity.person"
                      + " p on p.id=a.person_id and p.status='ACTIVE' where a.person_id=? and"
                      + " a.status='ACTIVE'",
                  Integer.class,
                  w.actor());
          Integer leased =
              db.queryForObject(
                  "select count(*) from identity.account_reset_outbox where id=? and lease_token=?"
                      + " and lease_until>clock_timestamp()",
                  Integer.class,
                  w.id(),
                  w.lease());
          if (target.size() != 1
              || !target.getFirst().equals(w.subject())
              || !Integer.valueOf(1).equals(administrator)
              || !Integer.valueOf(1).equals(leased))
            throw new IllegalStateException("ACCOUNT_RESET_AUTHORIZATION_REVOKED");
          Map remote =
              client
                  .get()
                  .uri(admin + "/admin/realms/{realm}/users/{user}", realm, w.subject())
                  .headers(h -> h.setBearerAuth(token))
                  .retrieve()
                  .body(Map.class);
          if (remote == null
              || !Boolean.TRUE.equals(remote.get("enabled"))
              || !(remote.get("email") instanceof String email)
              || email.isBlank())
            throw new IllegalStateException("ACCOUNT_RESET_TARGET_UNAVAILABLE");
          // Keycloak owns the recipient and the password action. No submitted email or temporary
          // password is accepted.
          client
              .post()
              .uri(admin + "/admin/realms/{realm}/users/{user}/logout", realm, w.subject())
              .headers(h -> h.setBearerAuth(token))
              .retrieve()
              .toBodilessEntity();
          var uri =
              org.springframework.web.util.UriComponentsBuilder.fromUriString(
                      admin + "/admin/realms/{realm}/users/{user}/execute-actions-email")
                  .queryParam("lifespan", 3600);
          if (!webClient.isBlank()) uri.queryParam("client_id", webClient);
          if (!redirect.isBlank()) uri.queryParam("redirect_uri", redirect);
          client
              .put()
              .uri(uri.buildAndExpand(realm, w.subject()).encode().toUri())
              .headers(h -> h.setBearerAuth(token))
              .contentType(MediaType.APPLICATION_JSON)
              .body(
                  Boolean.TRUE.equals(remote.get("emailVerified"))
                      ? List.of("UPDATE_PASSWORD")
                      : List.of("VERIFY_EMAIL", "UPDATE_PASSWORD"))
              .retrieve()
              .toBodilessEntity();
          db.update(
              "update identity.account_reset_outbox set"
                  + " status='SENT',error_code=null,lease_token=null,lease_until=null,updated_at=clock_timestamp()"
                  + " where id=? and lease_token=?",
              w.id(),
              w.lease());
        });
  }

  private String token() {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", clientId);
    form.add("client_secret", secret);
    Map response =
        client
            .post()
            .uri(issuer + "/protocol/openid-connect/token")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Map.class);
    if (response == null || !(response.get("access_token") instanceof String value))
      throw new IllegalStateException("KEYCLOAK_TOKEN_UNAVAILABLE");
    return value;
  }
}
