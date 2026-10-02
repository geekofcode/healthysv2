import {apiRequest} from './client';
import type {Page} from './organizations';

export type Specimen={id:string;specimenNumber:string;specimenType:string;collectedAt:string;collectedBy:string;status:string};
export type LabOrderItem={id:string;labExamCatalogId:string;examCode:string;examName:string;instructions?:string;status:string;specimens:Specimen[]};
export type LabResultItem={id:string;labOrderItemId:string;parameterCatalogId?:string;parameter?:string;value:string;unit?:string;referenceMin?:number;referenceMax?:number;interpretation?:string;abnormalFlag?:string};
export type LabResult={id:string;resultNumber:string;performedBy:string;validatedBy?:string;performedAt:string;validatedAt?:string;status:string;notes?:string;items:LabResultItem[]};
export type LabOrderSummary={id:string;orderNumber:string;patientId:string;laboratoryOrganizationId?:string;priority:string;orderedAt:string;status:string};
export type LabOrder=LabOrderSummary&{consultationId?:string;orderingProfessionalId:string;items:LabOrderItem[];results:LabResult[]};
export type CreateLabOrderInput={patientId:string;consultationId?:string;orderingProfessionalId:string;laboratoryOrganizationId?:string;priority:string;items:{labExamCatalogId:string;instructions?:string}[]};

export const laboratoryKeys={orders:(filters:Record<string,string>)=>['laboratory','orders',filters] as const,order:(id:string)=>['laboratory','orders',id] as const};
const qs=(values:Record<string,string>)=>{const p=new URLSearchParams();Object.entries(values).forEach(([k,v])=>v.trim()&&p.set(k,v.trim()));p.set('size','50');p.set('sort','orderedAt,desc');return p};
export const listLabOrders=(filters:Record<string,string>)=>apiRequest<Page<LabOrderSummary>>(`/lab-orders?${qs(filters)}`);
export const getLabOrder=(id:string)=>apiRequest<LabOrder>(`/lab-orders/${id}`);
export const createLabOrder=(input:CreateLabOrderInput)=>apiRequest<LabOrder>('/lab-orders',{method:'POST',body:JSON.stringify(input)});
const post=<T>(path:string,input?:unknown)=>apiRequest<T>(path,{method:'POST',...(input===undefined?{}:{body:JSON.stringify(input)})});
export const addLabOrderItem=(order:string,input:{labExamCatalogId:string;instructions?:string})=>post<LabOrderItem>(`/lab-orders/${order}/items`,input);
export const collectSpecimen=(order:string,item:string,input:Record<string,unknown>)=>post<Specimen>(`/lab-orders/${order}/items/${item}/specimens`,input);
export const receiveSpecimen=(order:string,specimen:string)=>post<Specimen>(`/lab-orders/${order}/specimens/${specimen}/receive`);
export const rejectSpecimen=(order:string,specimen:string)=>post<Specimen>(`/lab-orders/${order}/specimens/${specimen}/reject`);
export const createLabResult=(order:string,input:Record<string,unknown>)=>post<LabResult>(`/lab-orders/${order}/results`,input);
export const addLabResultItem=(order:string,result:string,input:Record<string,unknown>)=>post<LabResultItem>(`/lab-orders/${order}/results/${result}/items`,input);
export const validateLabResult=(order:string,result:string,validatedBy:string)=>post<LabResult>(`/lab-orders/${order}/results/${result}/validate`,{validatedBy});
export const cancelLabOrder=(id:string)=>post<LabOrder>(`/lab-orders/${id}/cancel`);
export const validReferenceRange=(min?:number,max?:number)=>min===undefined||max===undefined||max>=min;
