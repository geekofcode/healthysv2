package org.novasos.healthysv2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KeycloakProfessionalRoleSyncWorkerTests {
    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void deliveryFailureRemainsRetryableWithoutPersistingResponseOrCredentials() {
        JdbcTemplate jdbc=mock(JdbcTemplate.class);
        UUID subject=UUID.randomUUID();
        when(jdbc.query(anyString(),any(RowMapper.class),any(Object[].class))).thenAnswer(invocation -> {
            String sql=invocation.getArgument(0);
            if (sql.contains("SELECT status, professional_id")) return List.of(true);
            if (sql.contains("SELECT keycloak_user_id")) return List.of(subject);
            return List.of(new KeycloakProfessionalRoleSyncWorker.Delivery(subject,"medecin",true));
        });
        // No-parameter polling query uses the other JdbcTemplate overload.
        when(jdbc.query(anyString(),any(RowMapper.class))).thenReturn(List.of(new KeycloakProfessionalRoleSyncWorker.Delivery(subject,"medecin",true)));
        when(jdbc.queryForObject(anyString(),eq(Boolean.class),eq(subject))).thenReturn(true);
        var builder=RestClient.builder();
        var server=MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://keycloak.test/realms/healthys/protocol/openid-connect/token"))
                .andRespond(withServerError());
        var worker=new KeycloakProfessionalRoleSyncWorker(jdbc,null,builder.build());
        assertThat(worker.deliverOne()).isTrue();
        verify(jdbc).update(eq("UPDATE professional.role_sync_outbox SET status='FAILED', attempts=attempts+1, last_error='KEYCLOAK_SYNC_FAILED', updated_at=now() WHERE subject_id=?"),eq(subject));
        server.verify();
    }
}
