import {apiRequest} from './client';
import type {AccessProfile} from './accessProfiles';
export type PatientProfileInput={kind:'SELF'|'MINOR';firstName:string;lastName:string;birthDate:string;gender?:string;guardianRelationship?:'PARENT'|'GUARDIAN';guardianBirthDate?:string};
export const createPersonalPatientProfile=(input:PatientProfileInput,key:string)=>apiRequest<AccessProfile>('/me/patient-profiles',{method:'POST',headers:{'Idempotency-Key':key},body:JSON.stringify(input)});
