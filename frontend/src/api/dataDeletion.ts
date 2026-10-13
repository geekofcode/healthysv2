import {apiRequest} from './client';
export type DataDeletionRequest={id:string;status:'QUEUED'|'RUNNING'|'COMPLETED'|'FAILED';createdAt:string;completedAt?:string;removedCount:number;anonymizedCount:number;retainedCount:number;notes?:string};
export const listMyDataDeletionRequests=()=>apiRequest<DataDeletionRequest[]>('/me/data-deletion');
export const requestMyDataDeletion=(idempotencyKey:string)=>apiRequest<DataDeletionRequest>('/me/data-deletion',{method:'POST',headers:{'Idempotency-Key':idempotencyKey}});
