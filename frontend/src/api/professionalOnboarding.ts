import {apiRequest} from './client';
import {validAccessToken} from '../auth/keycloak';
export type Profession='medecin'|'nurse'|'laboratoire';
export type IdentityDocumentType='PASSPORT'|'NATIONAL_ID'|'DRIVING_LICENSE';
export type DocumentKind='ID_FRONT'|'ID_BACK'|'LICENSE';
export type DossierInput={profession:Profession;licenseNumber:string;issuingAuthority:string;countryId:string;specialityCatalogId?:string;specialityName?:string;identityDocumentType?:IdentityDocumentType;identityExpiresOn?:string};
export type Dossier=DossierInput & {id:string;personId:string;keycloakUserId:string;firstName?:string;lastName?:string;roleSyncStatus?:string;status:'DRAFT'|'SUBMITTED'|'APPROVED'|'REJECTED'|'SUSPENDED';reason?:string;proofUploaded:boolean;identityFrontUploaded?:boolean;identityBackUploaded?:boolean;professionalId?:string;createdAt:string;updatedAt:string};
export type Invitation={id:string;email:string;organizationId:string;position:string;token?:string;expiresAt:string;status:string};
export type Affiliation={id:string;professionalId:string;organizationId:string;organizationName?:string;status:string};
const base='/professional-onboarding';
export const onboardingKeys={me:['professional-onboarding','me'] as const,requests:['professional-onboarding','requests'] as const,organization:(id:string)=>['professional-onboarding','organization',id] as const};
export const getMyDossier=()=>apiRequest<Dossier|null>(`${base}/me`);
export const saveDossier=(input:DossierInput)=>apiRequest<Dossier>(`${base}/me`,{method:'PUT',body:JSON.stringify(input)});
export const submitDossier=()=>apiRequest<Dossier>(`${base}/me/submit`,{method:'POST'});
export const uploadProof=(file:File)=>{const body=new FormData();body.append('file',file);return apiRequest<Dossier>(`${base}/me/proof`,{method:'POST',body});};
export const uploadDocument=({kind,file}:{kind:DocumentKind;file:File})=>{const body=new FormData();body.append('file',file);return apiRequest<Dossier>(`${base}/me/documents/${kind}`,{method:'POST',body});};
export const listDossiers=()=>apiRequest<Dossier[]>(`${base}/requests`);
export const reviewDossier=(id:string,decision:'APPROVE'|'REJECT'|'SUSPEND',reason:string)=>apiRequest<Dossier>(`${base}/requests/${id}/review`,{method:'POST',body:JSON.stringify({decision,reason})});
type Options={countries:{id:string;name:string;iso2:string}[];specialities:{id:string;name:string}[]};
export const listCountries=async()=>(await apiRequest<Options>('/registration-options')).countries;
export const listSpecialities=async()=>(await apiRequest<Options>('/registration-options')).specialities;
export const createInvitation=(input:{email:string;organizationId:string;position:string})=>apiRequest<Invitation>(`${base}/invitations`,{method:'POST',body:JSON.stringify(input)});
export const listInvitations=(organizationId:string)=>apiRequest<Invitation[]>(`${base}/invitations?organizationId=${encodeURIComponent(organizationId)}`);
export const listAffiliations=(organizationId:string)=>apiRequest<Affiliation[]>(`${base}/affiliations?organizationId=${encodeURIComponent(organizationId)}`);
export const revokeAffiliation=(id:string)=>apiRequest<void>(`${base}/affiliations/${id}`,{method:'DELETE'});
export const acceptInvitation=(token:string)=>apiRequest<Affiliation>(`${base}/invitations/accept`,{method:'POST',body:JSON.stringify({token})});
export async function downloadDocument(id:string,kind:DocumentKind){const response=await fetch(`${import.meta.env.VITE_API_BASE_URL}${base}/requests/${id}/documents/${kind}`,{headers:{Authorization:`Bearer ${await validAccessToken()}`}});if(!response.ok)throw new Error('Download failed');const url=URL.createObjectURL(await response.blob());const a=document.createElement('a');a.href=url;a.download=`professional-${kind.toLowerCase()}`;a.click();URL.revokeObjectURL(url);}

export const listMyAffiliations=()=>apiRequest<Affiliation[]>(`${base}/me/affiliations`);
export const createCountry=(input:{iso2:string;name:string})=>apiRequest('/admin/registration-options/countries',{method:'POST',body:JSON.stringify(input)});

export type DirectoryProfessional={professionalId:string;personId:string;firstName:string;lastName:string;profession:string};
export const listProfessionalDirectory=()=>apiRequest<DirectoryProfessional[]>(`${base}/directory`);

export const getMyProfessional=()=>apiRequest<{id:string;professionalType:string}|null>(`${base}/me/professional`);

export const downloadProof=(id:string)=>downloadDocument(id,'LICENSE');
