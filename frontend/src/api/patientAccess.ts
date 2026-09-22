import {apiRequest} from './client';

export type CareRelationship={id:string;patientId:string;professionalId:string;organizationId:string;relationshipType:string;startDate:string;endDate?:string;status:string;createdAt:string};
export type Consent={id:string;patientId:string;granteePersonId?:string;granteeOrganizationId?:string;scope:string;purpose?:string;reason?:string;grantedAt:string;expiresAt?:string;revokedAt?:string;status:string;createdAt:string;updatedAt:string};
export type AccessDecision={patientId:string;scope:string;action:string;allowed:boolean;decision:string};
export type CareRelationshipInput={professionalId:string;organizationId:string;relationshipType:string;startDate:string;endDate?:string};
export type ConsentInput={granteePersonId?:string;granteeOrganizationId?:string;scope:string;purpose?:string;reason?:string;grantedAt:string;expiresAt?:string};

export const patientAccessKeys={relationships:(patientId:string)=>['patients',patientId,'care-relationships'] as const,consents:(patientId:string)=>['patients',patientId,'consents'] as const,decision:(patientId:string,scope:string,action:string)=>['patients',patientId,'access',scope,action] as const};
const root=(patientId:string)=>`/patients/${patientId}`;
export const listCareRelationships=(patientId:string)=>apiRequest<CareRelationship[]>(`${root(patientId)}/care-relationships`);
export const createCareRelationship=(patientId:string,input:CareRelationshipInput)=>apiRequest<CareRelationship>(`${root(patientId)}/care-relationships`,{method:'POST',body:JSON.stringify(input)});
export const endCareRelationship=(patientId:string,id:string)=>apiRequest<CareRelationship>(`${root(patientId)}/care-relationships/${id}/end`,{method:'POST',body:JSON.stringify({endedAt:new Date().toISOString()})});
export const listConsents=(patientId:string)=>apiRequest<Consent[]>(`${root(patientId)}/consents`);
export const grantConsent=(patientId:string,input:ConsentInput)=>apiRequest<Consent>(`${root(patientId)}/consents`,{method:'POST',body:JSON.stringify(input)});
export const revokeConsent=(patientId:string,id:string,reason:string)=>apiRequest<Consent>(`${root(patientId)}/consents/${id}/revoke`,{method:'POST',body:JSON.stringify({reason,revokedAt:new Date().toISOString()})});
export const checkPatientAccess=(patientId:string,scope:string,action:string)=>apiRequest<AccessDecision>(`${root(patientId)}/access?${new URLSearchParams({scope,action})}`);
export const validConsentGrantee=(person?:string,organization?:string)=>Boolean(person)!==Boolean(organization);
