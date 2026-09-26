import {apiRequest} from './client';
import type {Page} from './organizations';

export type AuditLog={id:string;actorPersonId?:string;organizationId?:string;module:string;entityType:string;entityId?:string;action:string;oldValue?:unknown;newValue?:unknown;correlationId?:string;ipAddress?:string;userAgent?:string;occurredAt:string};
export type DataAccessLog={id:string;actorPersonId?:string;patientId?:string;organizationId?:string;resourceType:string;resourceId?:string;action:string;accessReason?:string;accessContext?:unknown;ipAddress?:string;correlationId?:string;occurredAt:string};
export type AuthenticationLog={id:string;keycloakUserId?:string;personId?:string;eventType:string;success:boolean;ipAddress?:string;userAgent?:string;details?:unknown;occurredAt:string};
export type SecurityEvent={id:string;actorPersonId?:string;eventType:string;severity:string;description?:string;details?:unknown;ipAddress?:string;occurredAt:string;resolvedAt?:string};
export type PlatformOverview={organizations:number;patients:number;professionals:number;activeAppointments:number;openInvoices:number;unreadNotifications:number;auditEventsToday:number;deniedAccessesToday:number;unresolvedSecurityEvents:number};
export type DailyAuditCount={date:string;changes:number;dataAccesses:number;deniedAccesses:number;authFailures:number};
export type AuditDashboard={days:number;totalChanges:number;totalDataAccesses:number;deniedAccesses:number;authenticationFailures:number;unresolvedSecurityEvents:number;daily:DailyAuditCount[];recentSecurityEvents:SecurityEvent[]};
export type AuditFilters={module?:string;action?:string;actorPersonId?:string;organizationId?:string;patientId?:string;resourceType?:string;from?:string;to?:string};

const query=(values:Record<string,string|number|boolean|undefined>)=>{const q=new URLSearchParams();Object.entries(values).forEach(([key,value])=>{if(value!==undefined&&String(value).trim())q.set(key,String(value).trim())});return q.size?`?${q}`:''};
export const adminKeys={overview:['admin','overview'] as const,dashboard:(days:number)=>['admin','audit-dashboard',days] as const,audit:(filters:AuditFilters)=>['admin','audit',filters] as const,access:(filters:AuditFilters)=>['admin','access',filters] as const,auth:['admin','authentication'] as const,security:['admin','security'] as const};
export const getPlatformOverview=()=>apiRequest<PlatformOverview>('/admin/overview');
export const getAuditDashboard=(days=7)=>apiRequest<AuditDashboard>(`/admin/audit-dashboard${query({days})}`);
export const listAuditLogs=(filters:AuditFilters={})=>apiRequest<Page<AuditLog>>(`/admin/audit-logs${query({...filters,size:50})}`);
export const listDataAccessLogs=(filters:AuditFilters={})=>apiRequest<Page<DataAccessLog>>(`/admin/data-access-logs${query({...filters,size:50})}`);
export const listAuthenticationLogs=()=>apiRequest<Page<AuthenticationLog>>('/admin/authentication-logs?size=50');
export const listSecurityEvents=()=>apiRequest<SecurityEvent[]>('/admin/security-events?limit=100');
export const resolveSecurityEvent=(id:string)=>apiRequest<SecurityEvent>(`/admin/security-events/${id}/resolve`,{method:'POST'});
