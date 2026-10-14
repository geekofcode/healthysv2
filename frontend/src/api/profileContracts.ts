import {apiRequest,ApiError} from './client';
import {keycloak,validAccessToken} from '../auth/keycloak';
import {selectedProfile} from '../auth/profileContext';
export type ContractCategory='PATIENT'|'HOSPITAL'|'STAFF'|'ADMIN';
export type ContractTemplate={id:string;category:ContractCategory;version:number;termsUrl:string;termsVersion:string;bodyText:string;renderedBodyText?:string|null;publishedAt?:string};
export type ContractState={profileId:string;signed:boolean;agreementId?:string;status:'UNSIGNED'|'SIGNED'|'PDF_READY';templateId?:string;templateVersion?:number;signedAt?:string;pdfReady:boolean;emailStatus?:'QUEUED'|'SENT'|'FAILED'};
export type ContractDraft={category:ContractCategory;termsUrl:string;termsVersion:string;bodyText:string};
const base=(profile:string)=>`/me/profile-contracts/${profile}`;
export const getProfileContract=(profile:string)=>apiRequest<ContractState>(base(profile));
export const getProfileContractTemplate=(profile:string)=>apiRequest<ContractTemplate>(`${base(profile)}/template`);
export const signProfileContract=(profile:string,input:{templateId:string;acceptTerms:boolean;signature:string;guardianAcknowledgement:boolean})=>apiRequest<ContractState>(`${base(profile)}/sign`,{method:'POST',body:JSON.stringify(input)});
export const listContractTemplates=()=>apiRequest<ContractTemplate[]>('/admin/contract-templates');
export const saveContractTemplate=(draft:ContractDraft,id?:string)=>apiRequest<ContractTemplate>(`/admin/contract-templates${id?`/${id}`:''}`,{method:id?'PUT':'POST',body:JSON.stringify(draft)});
export const publishContractTemplate=(id:string)=>apiRequest<ContractTemplate>(`/admin/contract-templates/${id}/publish`,{method:'POST'});
export async function downloadProfileContract(profile:string){const selection=selectedProfile(keycloak.tokenParsed?.sub);const token=await validAccessToken();const response=await fetch(`${import.meta.env.VITE_API_BASE_URL}${base(profile)}/pdf`,{headers:{Authorization:`Bearer ${token}`,Accept:'application/pdf'}});if(!response.ok)throw new ApiError(response.status,'CONTRACT_PDF_NOT_READY','Contract PDF is not available.');const bytes=await response.blob();if(JSON.stringify(selectedProfile(keycloak.tokenParsed?.sub))!==JSON.stringify(selection))throw new ApiError(409,'PROFILE_CHANGED','The active profile changed.');const url=URL.createObjectURL(bytes);const a=document.createElement('a');a.href=url;a.download='healthys-contract.pdf';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
