import {apiRequest} from './client';
export type Intake = {observations?:string;vitals:Partial<Record<'temperatureC'|'systolicBp'|'diastolicBp'|'heartRate'|'respiratoryRate'|'oxygenSaturation'|'weightKg'|'heightCm',number>>};
export type CareEpisode = {id:string;patientId:string;organizationId:string;reason:string;specialityId?:string;status:string;createdAt:string;startedAt?:string;expiresAt?:string;followUpSeconds:number;visits:{id:string;professionalId?:string;scheduledAt?:string;status:string;followUp:boolean;startedAt?:string}[];intake?:Intake};
export const createCareEpisode = (input:{patientId:string;reason:string;specialityId:string;professionalId:string;scheduledAt:string})=>apiRequest<CareEpisode>('/care/episodes',{method:'POST',body:JSON.stringify(input)});
export const getCareEpisode = (id:string)=>apiRequest<CareEpisode>(`/care/episodes/${id}`);
export const listCareEpisodes = (patientId:string)=>apiRequest<CareEpisode[]>(`/care/episodes?patientId=${encodeURIComponent(patientId)}`);
export const saveCareIntake = (id:string,input:Intake)=>apiRequest<CareEpisode>(`/care/episodes/${id}/intake`,{method:'PUT',body:JSON.stringify(input)});

export const getCareIntake = (id:string)=>apiRequest<Intake|null>(`/care/episodes/${id}/intake`);

export type ReceptionOptions={doctors:{professionalId:string;firstName:string;lastName:string;specialityIds:string[]}[];specialities:{id:string;name:string}[]};
export const getReceptionOptions=()=>apiRequest<ReceptionOptions>('/care/episodes/reception-options');
