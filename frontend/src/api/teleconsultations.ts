import {apiRequest} from './client';

export type VideoParticipant={personId:string;role:string;joinedAt?:string;leftAt?:string};
export type WaitingRoomEntry={id:string;patientId:string;enteredAt:string;admittedAt?:string;status:string};
export type VideoSession={id:string;sessionNumber:string;appointmentId?:string;consultationId?:string;roomName:string;scheduledStart?:string;startedAt?:string;endedAt?:string;status:string;participants:VideoParticipant[];waitingRoom:WaitingRoomEntry[]};
export type JoinToken={serverUrl:string;roomName:string;token:string;expiresAt:string};
export type CreateVideoSession={appointmentId?:string;consultationId?:string;scheduledStart?:string};

export const teleconsultationKeys={all:['video-sessions'] as const,detail:(id:string)=>['video-sessions',id] as const};
export const listVideoSessions=()=>apiRequest<VideoSession[]>('/video-sessions');
export const getVideoSession=(id:string)=>apiRequest<VideoSession>(`/video-sessions/${id}`);
export const createVideoSession=(request:CreateVideoSession)=>apiRequest<VideoSession>('/video-sessions',{method:'POST',body:JSON.stringify(request)});
export const enterWaitingRoom=(id:string)=>apiRequest<WaitingRoomEntry>(`/video-sessions/${id}/waiting-room`,{method:'POST'});
export const admitPatient=(id:string,entry:string)=>apiRequest<WaitingRoomEntry>(`/video-sessions/${id}/waiting-room/${entry}/admit`,{method:'POST'});
export const startVideoSession=(id:string)=>apiRequest<VideoSession>(`/video-sessions/${id}/start`,{method:'POST'});
export const completeVideoSession=(id:string)=>apiRequest<VideoSession>(`/video-sessions/${id}/complete`,{method:'POST'});
export const requestJoinToken=(id:string)=>apiRequest<JoinToken>(`/video-sessions/${id}/token`,{method:'POST'});
export const leaveVideoSession=(id:string)=>apiRequest<void>(`/video-sessions/${id}/leave`,{method:'POST'});
