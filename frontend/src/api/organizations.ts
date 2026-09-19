import {apiRequest} from './client';

export type Bed = {id:string; bedNumber:string; status:string};
export type Room = {id:string; departmentId?:string; roomNumber:string; type?:string; status:string; beds:Bed[]};
export type CareService = {id:string; code:string; name:string; description?:string; status:string};
export type Department = {id:string; code:string; name:string; description?:string; status:string; services:CareService[]};
export type OrganizationSummary = {id:string; number:string; name:string; legalName?:string; status:string};
export type Organization = OrganizationSummary & {organizationTypeId?:string; phone?:string; email?:string; website?:string; createdAt:string; updatedAt:string; departments:Department[]; rooms:Room[]};
export type Page<T> = {content:T[]; page:{number:number;size:number;totalElements:number;totalPages:number;first:boolean;last:boolean}};
export type OrganizationInput = {number:string;name:string;legalName?:string;organizationTypeId?:string;phone?:string;email?:string;website?:string;status?:string};

export const organizationKeys = {all:['organizations'] as const, detail:(id:string)=>['organizations',id] as const};
export const listOrganizations = () => apiRequest<Page<OrganizationSummary>>('/organizations?size=100&sort=name,asc');
export const getOrganization = (id:string) => apiRequest<Organization>(`/organizations/${id}`);
export const createOrganization = (input:OrganizationInput) => apiRequest<Organization>('/organizations',{method:'POST',body:JSON.stringify(input)});
export const updateOrganization = (id:string,input:Omit<OrganizationInput,'number'>) => apiRequest<Organization>(`/organizations/${id}`,{method:'PUT',body:JSON.stringify(input)});
export const deleteOrganization = (id:string) => apiRequest<void>(`/organizations/${id}`,{method:'DELETE'});
export const addDepartment = (organizationId:string,input:{code:string;name:string;description?:string}) => apiRequest<Department>(`/organizations/${organizationId}/departments`,{method:'POST',body:JSON.stringify(input)});
export const addService = (organizationId:string,departmentId:string,input:{code:string;name:string;description?:string}) => apiRequest<CareService>(`/organizations/${organizationId}/departments/${departmentId}/services`,{method:'POST',body:JSON.stringify(input)});
export const addRoom = (organizationId:string,input:{departmentId?:string;roomNumber:string;type?:string}) => apiRequest<Room>(`/organizations/${organizationId}/rooms`,{method:'POST',body:JSON.stringify(input)});
export const addBed = (organizationId:string,roomId:string,input:{bedNumber:string}) => apiRequest<Bed>(`/organizations/${organizationId}/rooms/${roomId}/beds`,{method:'POST',body:JSON.stringify(input)});
