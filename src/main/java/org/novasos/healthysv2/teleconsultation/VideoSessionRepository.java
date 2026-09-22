package org.novasos.healthysv2.teleconsultation;

import static org.novasos.healthysv2.teleconsultation.api.TeleconsultationDtos.*;
import java.sql.*;import java.time.*;import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.stereotype.Repository;

@Repository
class VideoSessionRepository {
    private final JdbcTemplate jdbc; VideoSessionRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    record Session(UUID id,String number,UUID appointmentId,UUID consultationId,String room,Instant scheduled,Instant started,Instant ended,String status){}
    Optional<Session> find(UUID id){return jdbc.query("select id,session_number,appointment_id,consultation_id,external_room_id,scheduled_start,started_at,ended_at,status from teleconsultation.video_session where id=?",this::session,id).stream().findFirst();}
    List<Session> list(UUID person,boolean all,UUID adminOrganization){return jdbc.query("select distinct s.id,s.session_number,s.appointment_id,s.consultation_id,s.external_room_id,s.scheduled_start,s.started_at,s.ended_at,s.status from teleconsultation.video_session s left join teleconsultation.video_participant p on p.video_session_id=s.id left join appointment.appointment a on a.id=s.appointment_id left join consultation.consultation c on c.id=s.consultation_id where ? or p.person_id=? or (? is not null and coalesce(a.organization_id,c.organization_id)=?) order by coalesce(s.scheduled_start,s.started_at) desc nulls last",this::session,all,person,adminOrganization,adminOrganization);}
    List<ParticipantResponse> participants(UUID id){return jdbc.query("select person_id,role,joined_at,left_at from teleconsultation.video_participant where video_session_id=? order by role,person_id",(rs,n)->new ParticipantResponse(uuid(rs,"person_id"),rs.getString("role"),instant(rs,"joined_at"),instant(rs,"left_at")),id);}
    List<WaitingRoomResponse> waiting(UUID id){return jdbc.query("select id,patient_id,entered_at,admitted_at,status from teleconsultation.waiting_room where video_session_id=? order by entered_at",(rs,n)->new WaitingRoomResponse(uuid(rs,"id"),uuid(rs,"patient_id"),instant(rs,"entered_at"),instant(rs,"admitted_at"),rs.getString("status")),id);}
    boolean participant(UUID session,UUID person){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from teleconsultation.video_participant where video_session_id=? and person_id=?)",Boolean.class,session,person));}
    boolean organization(UUID session,UUID organization){return organization!=null&&Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from teleconsultation.video_session s left join appointment.appointment a on a.id=s.appointment_id left join consultation.consultation c on c.id=s.consultation_id where s.id=? and coalesce(a.organization_id,c.organization_id)=?)",Boolean.class,session,organization));}
    String role(UUID session,UUID person){return jdbc.query("select role from teleconsultation.video_participant where video_session_id=? and person_id=?",rs->rs.next()?rs.getString(1):null,session,person);}
    private Session session(ResultSet rs,int n)throws SQLException{return new Session(uuid(rs,"id"),rs.getString("session_number"),uuid(rs,"appointment_id"),uuid(rs,"consultation_id"),rs.getString("external_room_id"),instant(rs,"scheduled_start"),instant(rs,"started_at"),instant(rs,"ended_at"),rs.getString("status"));}
    private static UUID uuid(ResultSet rs,String name)throws SQLException{return rs.getObject(name,UUID.class);}private static Instant instant(ResultSet rs,String name)throws SQLException{var t=rs.getTimestamp(name);return t==null?null:t.toInstant();}
}
