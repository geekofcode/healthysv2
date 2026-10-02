import {apiRequest} from './client';
import type {Page} from './organizations';
import {validAccessToken} from '../auth/keycloak';
import i18n from '../i18n';

export type HealthDocument={id:string;documentNumber:string;patientId:string;categoryId?:string;categoryCode?:string;categoryName?:string;fileName:string;mimeType:string;sizeBytes:number;checksum:string;uploadedBy?:string;uploadedAt:string;status:string};
export type DocumentCategory={id:string;code:string;name:string};
export const documentKeys={list:(patientId:string)=>['documents',patientId] as const,categories:['documents','categories'] as const};
export const listDocuments=(patientId:string)=>apiRequest<Page<HealthDocument>>(`/documents?patientId=${encodeURIComponent(patientId)}&size=50&sort=uploadedAt,desc`);
export const listDocumentCategories=()=>apiRequest<DocumentCategory[]>('/documents/categories');
export async function uploadDocument(patientId:string,categoryId:string,file:File){const body=new FormData();body.append('file',file);const params=new URLSearchParams({patientId});if(categoryId)params.set('categoryId',categoryId);return apiRequest<HealthDocument>(`/documents?${params}`,{method:'POST',body});}
export const archiveDocument=(id:string)=>apiRequest<HealthDocument>(`/documents/${id}/archive`,{method:'POST'});
export async function downloadDocument(document:HealthDocument){const token=await validAccessToken();const response=await fetch(`${import.meta.env.VITE_API_BASE_URL}/documents/${document.id}/content`,{headers:{Authorization:`Bearer ${token}`,'Accept-Language':i18n.resolvedLanguage??'en'}});if(!response.ok)throw new Error(i18n.t('errors.requestFailed'));const blob=await response.blob();const url=URL.createObjectURL(blob);const anchor=window.document.createElement('a');anchor.href=url;anchor.download=document.fileName;anchor.click();URL.revokeObjectURL(url);}

