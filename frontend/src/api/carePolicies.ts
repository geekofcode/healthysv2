import {apiRequest} from './client';
export type SpecialityPolicy={specialityId:string;tariffId:string;followUpSeconds:number;version:number};
export const listSpecialityPolicies=()=>apiRequest<SpecialityPolicy[]>('/care/speciality-policies');
export const saveSpecialityPolicy=(specialityId:string,input:{tariffId:string;followUpSeconds:number;version:number})=>apiRequest<SpecialityPolicy>(`/care/speciality-policies/${specialityId}`,{method:'PUT',body:JSON.stringify(input)});
