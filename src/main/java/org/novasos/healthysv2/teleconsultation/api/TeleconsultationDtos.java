package org.novasos.healthysv2.teleconsultation.api;

import java.time.Instant;
import java.util.*;
import jakarta.validation.constraints.*;

public final class TeleconsultationDtos { private TeleconsultationDtos(){}
    public record CreateVideoSessionRequest(UUID appointmentId,UUID consultationId,Instant scheduledStart){@AssertTrue(message="{validation.teleconsultation.source}")public boolean isSourceValid(){return(appointmentId==null)!=(consultationId==null);}}
    public record ParticipantResponse(UUID personId,String role,Instant joinedAt,Instant leftAt){}
    public record WaitingRoomResponse(UUID id,UUID patientId,Instant enteredAt,Instant admittedAt,String status){}
    public record VideoSessionResponse(UUID id,String sessionNumber,UUID appointmentId,UUID consultationId,String roomName,Instant scheduledStart,Instant startedAt,Instant endedAt,String status,List<ParticipantResponse>participants,List<WaitingRoomResponse>waitingRoom){}
    public record JoinTokenResponse(String serverUrl,String roomName,String token,Instant expiresAt){}
}
