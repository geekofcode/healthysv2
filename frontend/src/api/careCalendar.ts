import {apiRequest} from './client';
export type CalendarVisit={visitId:string;episodeId:string;patientId:string;patientFirstName:string;patientLastName:string;scheduledAt:string;status:string;presence:'WAITING'|'PRESENT'|'CALLED';position:number;visitVersion:number};
export type CareCalendar={professionalId:string;date:string;timezone:string;version:number;visits:CalendarVisit[]};
export const getCareCalendar=(date:string,professionalId?:string)=>apiRequest<CareCalendar>(`/care/calendar?date=${encodeURIComponent(date)}${professionalId?`&professionalId=${encodeURIComponent(professionalId)}`:''}`);
export const setCarePresence=(input:{visitId:string;present:boolean;expectedVersion:number})=>apiRequest<CareCalendar>('/care/queue/presence',{method:'POST',body:JSON.stringify(input)});
export const callCareNext=(input:{professionalId:string;date:string;expectedVersion:number})=>apiRequest<CareCalendar>('/care/queue/next',{method:'POST',body:JSON.stringify(input)});
export const reorderCareQueue=(input:{professionalId:string;date:string;visitIds:string[];expectedVersion:number})=>apiRequest<CareCalendar>('/care/queue/order',{method:'PUT',body:JSON.stringify(input)});
