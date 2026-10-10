package org.novasos.healthysv2;

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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
@EnableScheduling
@ConditionalOnProperty(name="healthys.professional-role-sync.enabled", havingValue="true")
class KeycloakProfessionalRoleSyncWorker {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final RestClient client;
    private final String issuer;
    private final String admin;
    private final String realm;
    private final String clientId;
    private final String clientSecret;
    @org.springframework.beans.factory.annotation.Autowired
    KeycloakProfessionalRoleSyncWorker(JdbcTemplate jdbc, PlatformTransactionManager manager,
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${healthys.professional-role-sync.admin-server-url}") String admin,
            @Value("${healthys.professional-role-sync.realm}") String realm,
            @Value("${healthys.professional-role-sync.client-id}") String clientId,
            @Value("${healthys.professional-role-sync.client-secret}") String clientSecret) {
        if (admin.isBlank() || realm.isBlank() || clientId.isBlank() || clientSecret.isBlank())
            throw new IllegalArgumentException("Professional role sync requires Keycloak admin configuration");
        if (!"https".equals(URI.create(admin).getScheme()) || !"https".equals(URI.create(issuer).getScheme()))
            throw new IllegalArgumentException("Professional role sync requires HTTPS");
        this.jdbc=jdbc; this.transactions=new TransactionTemplate(manager);
        this.issuer=issuer; this.admin=admin.replaceAll("/+$", ""); this.realm=realm;
        this.clientId=clientId; this.clientSecret=clientSecret;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client=RestClient.builder().requestFactory(factory).build();
    }
    KeycloakProfessionalRoleSyncWorker(JdbcTemplate jdbc, TransactionTemplate transactions, RestClient client) {
        this.jdbc=jdbc; this.transactions=transactions; this.client=client;
        this.issuer="https://keycloak.test/realms/healthys";
        this.admin="https://keycloak.test"; this.realm="healthys";
        this.clientId="test-service"; this.clientSecret="secret-never-persisted";
    }
    @Scheduled(fixedDelayString="${healthys.professional-role-sync.poll-interval-ms:10000}")
    void deliver() {
        for (int i=0; i<10; i++) {
            Boolean processed=transactions.execute(tx -> deliverOne());
            if (!Boolean.TRUE.equals(processed)) break;
        }
    }
    boolean deliverOne() {
        var rows=jdbc.query("""
                SELECT subject_id, realm_role, enabled FROM professional.role_sync_outbox
                WHERE status IN ('PENDING', 'FAILED') AND
                (attempts=0 OR updated_at < now() - interval '30 seconds')
                ORDER BY updated_at LIMIT 1
                """, (rs,row) -> new Delivery(rs.getObject(1,UUID.class),rs.getString(2),rs.getBoolean(3)));
        if (rows.isEmpty()) return false;
        Delivery candidate=rows.getFirst();
        jdbc.query("SELECT keycloak_user_id FROM professional.registration_request WHERE keycloak_user_id=? FOR UPDATE", (rs,row) -> rs.getObject(1), candidate.subject());
        var locked=jdbc.query("SELECT subject_id, realm_role, enabled FROM professional.role_sync_outbox WHERE subject_id=? AND status IN ('PENDING', 'FAILED') AND (attempts=0 OR updated_at < now() - interval '30 seconds') FOR UPDATE SKIP LOCKED", (rs,row) -> new Delivery(rs.getObject(1,UUID.class),rs.getString(2),rs.getBoolean(3)),candidate.subject());
        if (locked.isEmpty()) return false;
        Delivery delivery=locked.getFirst();
        try {
            KeycloakProfessionalRoleSync.validateRole(delivery.role());
            // Lock the application as well: a concurrent suspension cannot race a role grant.
            var approved=jdbc.query("""
                    SELECT status, professional_id, profession FROM professional.registration_request
                    WHERE keycloak_user_id=? FOR UPDATE
                    """, (rs,row) -> "APPROVED".equals(rs.getString(1)) && rs.getObject(2)!=null && delivery.role().equals(rs.getString(3)), delivery.subject());
            boolean enabled=delivery.enabled() && !approved.isEmpty() && approved.getFirst();
            if (enabled) {
                enabled=Boolean.TRUE.equals(jdbc.queryForObject("""
                        SELECT p.status='ACTIVE' FROM professional.professional p
                        JOIN professional.registration_request r ON r.professional_id=p.id
                        WHERE r.keycloak_user_id=?
                        """,Boolean.class,delivery.subject()));
            }
            synchronize(delivery.subject(),delivery.role(),enabled);
            jdbc.update("UPDATE professional.role_sync_outbox SET status='SYNCED', attempts=attempts+1, last_error=NULL, updated_at=now() WHERE subject_id=?",delivery.subject());
        } catch (Exception exception) {
            // Never persist exception text: HTTP errors can contain credentials or personal data.
            jdbc.update("UPDATE professional.role_sync_outbox SET status='FAILED', attempts=attempts+1, last_error='KEYCLOAK_SYNC_FAILED', updated_at=now() WHERE subject_id=?",delivery.subject());
        }
        return true;
    }
    private void synchronize(UUID subject,String role,boolean enabled) {
        var form=new LinkedMultiValueMap<String,String>();
        form.add("grant_type","client_credentials"); form.add("client_id",clientId); form.add("client_secret",clientSecret);
        Map<?,?> token=client.post().uri(issuer+"/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
        if (token==null || !(token.get("access_token") instanceof String accessToken)) throw new IllegalStateException("Missing access token");
        var managedRoles = new java.util.ArrayList<>(List.of("medecin", "nurse", "laboratoire"));
        managedRoles.remove(role); managedRoles.add(role);
        for (String managedRole : managedRoles) {
            boolean grant = enabled && managedRole.equals(role);
            Map<?,?> representation=client.get().uri(admin+"/admin/realms/{realm}/roles/{role}",realm,managedRole)
                .headers(h -> h.setBearerAuth(accessToken)).retrieve().body(Map.class);
            if (representation==null) throw new IllegalStateException("Missing role");
            client.method(grant ? org.springframework.http.HttpMethod.POST : org.springframework.http.HttpMethod.DELETE)
                .uri(admin+"/admin/realms/{realm}/users/{user}/role-mappings/realm",realm,subject)
                .headers(h -> h.setBearerAuth(accessToken)).contentType(MediaType.APPLICATION_JSON)
                .body(List.of(representation)).retrieve().toBodilessEntity();
        }
    }
    record Delivery(UUID subject,String role,boolean enabled) {}
}
