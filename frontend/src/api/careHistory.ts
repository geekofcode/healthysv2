import {apiRequest} from './client';
export type CareHistoryEntry={visitId:string;episodeId:string;organizationId:string;organizationName:string;doctorName:string;revision:number;validatedAt:string;reason?:string;observations?:string;diagnosis?:string;treatment?:string;followUp?:string;historical:boolean};
export const getCarePatientHistory=(patientId:string)=>apiRequest<CareHistoryEntry[]>(`/patients/${encodeURIComponent(patientId)}/care-history`);
