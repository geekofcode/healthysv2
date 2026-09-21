import {apiRequest} from './client';
import type {Page} from './organizations';

export type PrescriptionStatus='ACTIVE'|'PARTIALLY_DISPENSED'|'DISPENSED'|'CANCELLED';
export type MedicationCatalog={id:string;code:string;name:string;genericName?:string;form?:string;strength?:string;atcCode?:string};
export type PrescriptionItem={id:string;medicationCatalogId:string;medicationCode:string;medicationName:string;dosage:string;frequency:string;route:string;duration:string;quantity:number;quantityDispensed:number;quantityRemaining:number;instructions?:string};
export type DispenseItem={id:string;prescriptionItemId:string;quantityDispensed:number;batchNumber:string};
export type Dispense={id:string;dispenseNumber:string;pharmacyOrganizationId:string;pharmacistId:string;dispensedAt:string;status:string;items:DispenseItem[]};
export type PrescriptionSummary={id:string;prescriptionNumber:string;patientId:string;organizationId?:string;prescribedAt:string;expiresAt?:string;status:PrescriptionStatus};
export type Prescription=PrescriptionSummary&{consultationId?:string;prescriberId:string;items:PrescriptionItem[];dispenses:Dispense[]};
export type MedicationStock={id:string;organizationId:string;medicationCatalogId:string;medicationCode:string;medicationName:string;batchNumber:string;quantity:number;expirationDate?:string;expired:boolean};
export type PrescriptionItemInput={medicationCatalogId:string;dosage:string;frequency:string;route:string;duration:string;quantity:number;instructions?:string};
export type PrescriptionInput={patientId:string;consultationId?:string;prescriberId:string;organizationId?:string;expiresAt?:string;items:PrescriptionItemInput[]};
export type DispenseInput={pharmacyOrganizationId:string;pharmacistId:string;items:{prescriptionItemId:string;quantityDispensed:number;batchNumber:string}[]};
export type ReplenishStockInput={organizationId:string;medicationCatalogId:string;batchNumber:string;quantity:number;expirationDate?:string};
export type PrescriptionFilters={patientId?:string;organizationId?:string;status?:string};

export const pharmacyKeys={prescriptions:(filters:PrescriptionFilters)=>['pharmacy','prescriptions',filters] as const,prescription:(id:string)=>['pharmacy','prescriptions',id] as const,catalog:(query:string)=>['pharmacy','catalog',query] as const,stocks:(organizationId:string,medicationCatalogId:string)=>['pharmacy','stocks',organizationId,medicationCatalogId] as const};
const queryString=(values:Record<string,string|undefined>)=>{const query=new URLSearchParams();Object.entries(values).forEach(([key,value])=>{if(value?.trim())query.set(key,value.trim())});const suffix=query.toString();return suffix?`?${suffix}`:''};
export const listPrescriptions=(filters:PrescriptionFilters)=>apiRequest<Page<PrescriptionSummary>>(`/prescriptions${queryString({...filters,size:'50',sort:'prescribedAt,desc'})}`);
export const getPrescription=(id:string)=>apiRequest<Prescription>(`/prescriptions/${id}`);
export const createPrescription=(input:PrescriptionInput)=>apiRequest<Prescription>('/prescriptions',{method:'POST',body:JSON.stringify(input)});
export const cancelPrescription=(id:string)=>apiRequest<Prescription>(`/prescriptions/${id}/cancel`,{method:'POST'});
export const dispensePrescription=(id:string,input:DispenseInput)=>apiRequest<Dispense>(`/prescriptions/${id}/dispenses`,{method:'POST',body:JSON.stringify(input)});
export const listMedicationCatalog=(query='')=>apiRequest<MedicationCatalog[]>(`/medication-catalog${queryString({query})}`);
export const listMedicationStocks=(organizationId='',medicationCatalogId='')=>apiRequest<MedicationStock[]>(`/medication-stocks${queryString({organizationId,medicationCatalogId})}`);
export const replenishMedicationStock=(input:ReplenishStockInput)=>apiRequest<MedicationStock>('/medication-stocks/replenish',{method:'POST',body:JSON.stringify(input)});
export const validPrescriptionItem=(item:PrescriptionItemInput)=>Boolean(item.medicationCatalogId&&item.dosage.trim()&&item.frequency.trim()&&item.route.trim()&&item.duration.trim()&&Number.isFinite(item.quantity)&&item.quantity>0);
export const validStockInput=(input:ReplenishStockInput)=>Boolean(input.organizationId&&input.medicationCatalogId&&input.batchNumber.trim()&&Number.isFinite(input.quantity)&&input.quantity>0&&(!input.expirationDate||input.expirationDate>=new Date().toISOString().slice(0,10)));
