import {apiRequest} from './client';

export type VitalSign={id:string;temperature?:number;weight?:number;height?:number;bmi?:number;systolicPressure?:number;diastolicPressure?:number;heartRate?:number;respiratoryRate?:number;oxygenSaturation?:number;measuredAt:string;measuredBy?:string};
export type Diagnosis={id:string;diagnosisCatalogId?:string;catalogCode?:string;catalogLabel?:string;diagnosisType:string;description?:string;status?:string;diagnosedAt:string};
export type Consultation={id:string;consultationNumber:string;patientId:string;professionalId:string;organizationId:string;appointmentId?:string;encounterId?:string;type:string;reason?:string;startedAt:string;completedAt?:string;status:string;vitalSigns:VitalSign[];diagnoses:Diagnosis[];notes:{id:string;authorProfessionalId:string;noteType:string;content:string;createdAt:string}[];observations:{id:string;authorProfessionalId?:string;type:string;value?:string;notes?:string;observedAt:string}[];treatments:{id:string;description:string;startDate?:string;endDate?:string;status?:string}[];followUps:{id:string;recommendedDate?:string;instructions:string;status?:string}[]};
export type PatientSummary={patientId:string;patientNumber:string;firstName:string;lastName:string;birthDate?:string;gender?:string;bloodGroup?:string;rhesus?:string;status:string;activeAllergies:string[];activeChronicDiseases:string[];activeFlags:string[]};
export type DiagnosisCatalog={id:string;code:string;system:string;label:string};
export type StartConsultationInput={patientId:string;professionalId:string;organizationId:string;appointmentId?:string;encounterId?:string;type:string;reason?:string};

export const consultationKeys={detail:(id:string)=>['consultations',id] as const,summary:(id:string)=>['consultations',id,'summary'] as const,catalog:(query:string)=>['diagnosis-catalog',query] as const};
export const startConsultation=(input:StartConsultationInput)=>apiRequest<Consultation>('/consultations',{method:'POST',body:JSON.stringify(input)});
export const getConsultation=(id:string)=>apiRequest<Consultation>(`/consultations/${id}`);
export const getPatientSummary=(id:string)=>apiRequest<PatientSummary>(`/consultations/${id}/patient-summary`);
const add=<T>(id:string,path:string,input:unknown)=>apiRequest<T>(`/consultations/${id}/${path}`,{method:'POST',body:JSON.stringify(input)});
export const addVitalSign=(id:string,input:Record<string,unknown>)=>add<VitalSign>(id,'vital-signs',input);
export const addDiagnosis=(id:string,input:Record<string,unknown>)=>add<Diagnosis>(id,'diagnoses',input);
export const addConsultationNote=(id:string,input:Record<string,unknown>)=>add<Consultation['notes'][number]>(id,'notes',input);
export const addClinicalObservation=(id:string,input:Record<string,unknown>)=>add<Consultation['observations'][number]>(id,'observations',input);
export const addTreatment=(id:string,input:Record<string,unknown>)=>add<Consultation['treatments'][number]>(id,'treatments',input);
export const addFollowUp=(id:string,input:Record<string,unknown>)=>add<Consultation['followUps'][number]>(id,'follow-ups',input);
export const completeConsultation=(id:string)=>apiRequest<Consultation>(`/consultations/${id}/complete`,{method:'POST'});
export const searchDiagnosisCatalog=(query='')=>apiRequest<DiagnosisCatalog[]>(`/diagnosis-catalog?${new URLSearchParams({query})}`);
export const hasVitalValue=(input:Record<string,unknown>)=>['temperature','weight','height','systolicPressure','diastolicPressure','heartRate','respiratoryRate','oxygenSaturation'].some(key=>input[key]!==''&&input[key]!=null);
