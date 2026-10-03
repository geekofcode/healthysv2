package org.novasos.healthysv2.notification;

import java.sql.Timestamp;
import java.time.*;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@EnableScheduling
class PushDeliveryWorker {
    private final JdbcTemplate jdbc;
    private final PushGateway gateway;
    PushDeliveryWorker(JdbcTemplate jdbc,PushGateway gateway) {this.jdbc=jdbc;this.gateway=gateway;}
    @Scheduled(fixedDelayString="${healthys.push.poll-interval-ms:10000}")
    @Transactional
    public void dispatch() {
        if(!gateway.enabled()) return;
        // One worker at a time also synchronizes device revocation and token rotation.
        jdbc.queryForObject("select pg_advisory_xact_lock(1810)",Object.class);
        jdbc.update("delete from notification.push_outbox where status<>'PENDING' and created_at<now()-interval '30 days'");
        var jobs=jdbc.query("select id,notification_id,installation_id,person_id,attempts from notification.push_outbox where status='PENDING' and next_attempt_at<=now() order by next_attempt_at,id limit 5 for update skip locked",(rs,row)->new Job((UUID)rs.getObject(1),(UUID)rs.getObject(2),(UUID)rs.getObject(3),(UUID)rs.getObject(4),rs.getInt(5)));
        for(Job job:jobs) deliver(job);
    }
    private void deliver(Job job) {
        var targets=jdbc.query("select d.token,p.push_enabled,p.locale,p.quiet_hours_start,p.quiet_hours_end,n.expires_at,nr.read_at from notification.push_device d join notification.notification_preference p on p.person_id=d.person_id join notification.notification n on n.id=? join notification.notification_recipient nr on nr.notification_id=n.id and nr.person_id=d.person_id where d.installation_id=? and d.person_id=? and d.active",(rs,row)->new Target(rs.getString(1),rs.getBoolean(2),rs.getString(3),rs.getTime(4)==null?null:rs.getTime(4).toLocalTime(),rs.getTime(5)==null?null:rs.getTime(5).toLocalTime(),rs.getTimestamp(6)==null?null:rs.getTimestamp(6).toInstant(),rs.getTimestamp(7)!=null),job.notification(),job.installation(),job.person());
        Instant now=Instant.now();
        if(targets.isEmpty()) {finish(job,"CANCELLED","DEVICE_INACTIVE",job.attempts());return;}
        Target target=targets.getFirst();
        if(!target.enabled()||target.read()||(target.expiry()!=null&&!target.expiry().isAfter(now))) {finish(job,"CANCELLED","PREFERENCE_READ_OR_EXPIRED",job.attempts());return;}
        Instant quietUntil=quietUntil(target.start(),target.end(),now);
        if(quietUntil!=null) {jdbc.update("update notification.push_outbox set next_attempt_at=? where id=?",Timestamp.from(quietUntil),job.id());return;}
        PushGateway.Result result;
        try {result=gateway.send(target.token(),job.notification(),target.locale());}
        catch(RuntimeException exception) {result=new PushGateway.Result(PushGateway.Outcome.TRANSIENT_FAILURE,null);}
        int attempts=job.attempts()+1;
        if(result.outcome()==PushGateway.Outcome.ACCEPTED) {
            finish(job,"SENT",null,attempts);
            jdbc.update("insert into notification.notification_delivery(notification_id,person_id,channel,provider,provider_message_id,sent_at,status) values (?,?,'PUSH','FCM',?,now(),'SENT')",job.notification(),job.person(),result.messageId());
        } else if(result.outcome()==PushGateway.Outcome.TRANSIENT_FAILURE&&attempts<5) {
            jdbc.update("update notification.push_outbox set attempts=?,next_attempt_at=?,last_error='TRANSIENT_FAILURE' where id=?",attempts,Timestamp.from(now.plusSeconds(Math.min(3600,30L<<(attempts-1)))),job.id());
        } else {
            finish(job,"FAILED",result.outcome().name(),attempts);
            jdbc.update("insert into notification.notification_delivery(notification_id,person_id,channel,provider,failed_at,error_message,status) values (?,?,'PUSH','FCM',now(),?,'FAILED')",job.notification(),job.person(),result.outcome().name());
            if(result.outcome()==PushGateway.Outcome.UNREGISTERED) {
                jdbc.update("update notification.push_device set active=false,token=null,token_hash=null,revocation_hash=null,updated_at=now() where installation_id=?",job.installation());
                jdbc.update("update notification.push_outbox set status='CANCELLED',last_error='DEVICE_UNREGISTERED' where installation_id=? and status='PENDING'",job.installation());
            }
        }
    }
    static Instant quietUntil(LocalTime start,LocalTime end,Instant now) {
        if(start==null||end==null) return null;
        var utc=now.atZone(ZoneOffset.UTC);LocalTime time=utc.toLocalTime();
        boolean quiet=start.isBefore(end)?!time.isBefore(start)&&time.isBefore(end):!time.isBefore(start)||time.isBefore(end);
        if(!quiet) return null;
        LocalDate date=utc.toLocalDate();if(!time.isBefore(end)) date=date.plusDays(1);
        return date.atTime(end).toInstant(ZoneOffset.UTC);
    }
    private void finish(Job job,String status,String error,int attempts) {jdbc.update("update notification.push_outbox set status=?,last_error=?,attempts=? where id=?",status,error,attempts,job.id());}
    private record Job(UUID id,UUID notification,UUID installation,UUID person,int attempts) {}
    private record Target(String token,boolean enabled,String locale,LocalTime start,LocalTime end,Instant expiry,boolean read) {}
}
