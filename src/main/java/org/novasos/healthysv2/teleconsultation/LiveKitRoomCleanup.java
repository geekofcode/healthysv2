package org.novasos.healthysv2.teleconsultation;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Durable room deletion is visible only after the completion transaction commits. */
@Service
class LiveKitRoomCleanup {
    private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(LiveKitRoomCleanup.class);
    private final JdbcTemplate jdbc;
    private final LiveKitRoomGateway gateway;
    LiveKitRoomCleanup(JdbcTemplate jdbc,LiveKitRoomGateway gateway){this.jdbc=jdbc;this.gateway=gateway;}
    void createRoom(String room){gateway.create(room);}
    void schedule(UUID session,String room){
        jdbc.update("insert into teleconsultation.room_cleanup(video_session_id,room_name) values (?,?) on conflict(video_session_id) do nothing",session,room);
    }
    @Scheduled(fixedDelayString="${healthys.livekit.cleanup-interval-ms:10000}")
    @Transactional
    public void dispatch(){
        var rooms=jdbc.query("select video_session_id,room_name from teleconsultation.room_cleanup where completed_at is null and failed_at is null and next_attempt_at<=now() order by next_attempt_at limit 5 for update skip locked",(rs,n)->new Pending((UUID)rs.getObject(1),rs.getString(2)));
        for(var room:rooms){
            if(gateway.delete(room.room()))jdbc.update("update teleconsultation.room_cleanup set completed_at=now(),attempts=attempts+1 where video_session_id=?",room.session());
            else {
                jdbc.update("update teleconsultation.room_cleanup set attempts=attempts+1,last_error='PROVIDER_UNAVAILABLE',next_attempt_at=now()+interval '30 seconds',failed_at=case when attempts>=19 then now() else null end where video_session_id=?",room.session());
                if(Boolean.TRUE.equals(jdbc.queryForObject("select failed_at is not null from teleconsultation.room_cleanup where video_session_id=?",Boolean.class,room.session())))log.warn("LiveKit room deletion needs operator retry for video session {}",room.session());
            }
        }
        jdbc.update("delete from teleconsultation.room_cleanup where completed_at<now()-interval '30 days'");
    }
    private record Pending(UUID session,String room){}
}
