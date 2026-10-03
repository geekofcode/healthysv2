package org.novasos.healthysv2.notification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.novasos.healthysv2.shared.api.error.BusinessRuleException;

@Service
@Transactional
class PushDevices {
    private final JdbcTemplate jdbc;
    private final PushGateway gateway;
    PushDevices(JdbcTemplate jdbc, PushGateway gateway) { this.jdbc=jdbc; this.gateway=gateway; }

    DeviceResponse register(UUID installation, DeviceRequest request) {
        UUID person=currentPerson();
        // Serialize all device mutations, including concurrent token rotations and account switches.
        jdbc.queryForObject("select pg_advisory_xact_lock(1810)", Object.class);
        UUID owner=jdbc.query("select person_id from notification.push_device where installation_id=? and active", rs->rs.next()?(UUID)rs.getObject(1):null, installation);
        if(owner!=null&&!owner.equals(person)) throw new AccessDeniedException("DEVICE_OWNER_MISMATCH");
        Integer count=jdbc.queryForObject("select count(*) from notification.push_device where person_id=? and active and installation_id<>?",Integer.class,person,installation);
        if(count!=null&&count>=10) throw new BusinessRuleException("DEVICE_LIMIT_REACHED","error.notification.device-limit");
        String hash=hash(request.token());
        UUID tokenInstallation=jdbc.query("select installation_id from notification.push_device where token_hash=?",rs->rs.next()?(UUID)rs.getObject(1):null,hash);
        if(tokenInstallation!=null&&!tokenInstallation.equals(installation)) throw new BusinessRuleException("DEVICE_TOKEN_ALREADY_REGISTERED","error.notification.device-token");

        String revocation=request.revocationToken();
        jdbc.update("insert into notification.push_device(installation_id,person_id,token,token_hash,platform,revocation_hash) values (?,?,?,?,?,?) on conflict(installation_id) do update set person_id=excluded.person_id,token=excluded.token,token_hash=excluded.token_hash,platform=excluded.platform,revocation_hash=excluded.revocation_hash,active=true,updated_at=now()",installation,person,request.token(),hash,request.platform(),hash(revocation));
        return jdbc.queryForObject("select platform,active,updated_at from notification.push_device where installation_id=?",(rs,row)->new DeviceResponse(installation,rs.getString(1),rs.getBoolean(2),rs.getTimestamp(3).toInstant(),revocation),installation);
    }
    void revoke(UUID installation) {
        UUID person=currentPerson();
        jdbc.queryForObject("select pg_advisory_xact_lock(1810)", Object.class);
        UUID owner=jdbc.query("select person_id from notification.push_device where installation_id=?",rs->rs.next()?(UUID)rs.getObject(1):null,installation);
        if(owner!=null&&!owner.equals(person)) throw new AccessDeniedException("DEVICE_OWNER_MISMATCH");
        jdbc.update("update notification.push_device set active=false,token=null,token_hash=null,revocation_hash=null,updated_at=now() where installation_id=? and person_id=?",installation,person);
        jdbc.update("update notification.push_outbox set status='CANCELLED',last_error='DEVICE_REVOKED' where installation_id=? and person_id=? and status='PENDING'",installation,person);
    }
    void revokeCapability(RevocationRequest request) {
        jdbc.queryForObject("select pg_advisory_xact_lock(1810)",Object.class);
        int changed=jdbc.update("update notification.push_device set active=false,token=null,token_hash=null,revocation_hash=null,updated_at=now() where installation_id=? and revocation_hash=?",request.installationId(),hash(request.revocationToken()));
        if(changed>0) jdbc.update("update notification.push_outbox set status='CANCELLED',last_error='DEVICE_REVOKED' where installation_id=? and status='PENDING'",request.installationId());
    }
    void enqueue(UUID notification) {
        if(!gateway.enabled()) return;
        jdbc.queryForObject("select pg_advisory_xact_lock(1810)",Object.class);
        Integer pending=jdbc.queryForObject("select count(*) from notification.push_outbox where status='PENDING'",Integer.class);
        jdbc.update("insert into notification.push_outbox(notification_id,installation_id,person_id) select nr.notification_id,d.installation_id,nr.person_id from notification.notification_recipient nr join notification.push_device d on d.person_id=nr.person_id and d.active join notification.notification_preference p on p.person_id=nr.person_id and p.push_enabled where nr.notification_id=? order by d.installation_id limit ? on conflict do nothing",notification,Math.max(0,10000-(pending==null?0:pending)));
        jdbc.update("insert into notification.notification_delivery(notification_id,person_id,channel,provider,destination,failed_at,error_message,status) select nr.notification_id,nr.person_id,'PUSH','FCM',d.installation_id::text,now(),'QUEUE_FULL','FAILED' from notification.notification_recipient nr join notification.push_device d on d.person_id=nr.person_id and d.active join notification.notification_preference p on p.person_id=nr.person_id and p.push_enabled where nr.notification_id=? and not exists(select 1 from notification.push_outbox o where o.notification_id=nr.notification_id and o.installation_id=d.installation_id)",notification);
    }
    private UUID currentPerson() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(!(auth instanceof JwtAuthenticationToken jwt)) throw new AccessDeniedException("JWT_REQUIRED");
        if(jwt.getToken().getSubject()==null) throw new AccessDeniedException("INVALID_SUBJECT");
        try {
            UUID subject=UUID.fromString(jwt.getToken().getSubject());
            UUID person=jdbc.query("select id from identity.person where keycloak_user_id=? and status='ACTIVE'",rs->rs.next()?(UUID)rs.getObject(1):null,subject);
            if(person==null) throw new AccessDeniedException("PERSON_CONTEXT_MISSING");
            return person;
        } catch(IllegalArgumentException exception) {throw new AccessDeniedException("INVALID_SUBJECT");}
    }
    private String hash(String token) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException exception) {throw new IllegalStateException(exception);}
    }
    record DeviceRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=4096) String token,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Pattern(regexp="ANDROID|IOS") String platform,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min=43,max=43) @jakarta.validation.constraints.Pattern(regexp="[A-Za-z0-9_-]{43}") String revocationToken) {}
    record DeviceResponse(UUID installationId,String platform,boolean active,Instant updatedAt,String revocationToken) {}
    record RevocationRequest(@jakarta.validation.constraints.NotNull UUID installationId,
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min=43,max=43) @jakarta.validation.constraints.Pattern(regexp="[A-Za-z0-9_-]{43}") String revocationToken) {}
}
