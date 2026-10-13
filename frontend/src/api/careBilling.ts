import {apiRequest} from './client';
import type {Invoice} from './billing';

export type CareTariff={id:string;organizationId:string;code:string;description:string;currency:string;unitPrice:number;taxRate:number;active:boolean;version:number};
export type TariffInput=Omit<CareTariff,'id'|'version'>&{version?:number};
export type CashReceipt={id:string;receiptNumber:string;invoiceId:string;operationType:'PAYMENT'|'REFUND';amount:number;currency:string;reason?:string;occurredAt:string;cashierPersonId:string};
const base='/billing/care';
export const listCareTariffs=(organizationId:string)=>apiRequest<CareTariff[]>(`${base}/tariffs?organizationId=${encodeURIComponent(organizationId)}`);
export const createCareTariff=(input:TariffInput)=>apiRequest<CareTariff>(`${base}/tariffs`,{method:'POST',body:JSON.stringify(input)});
export const updateCareTariff=(id:string,input:TariffInput)=>apiRequest<CareTariff>(`${base}/tariffs/${id}`,{method:'PUT',body:JSON.stringify(input)});
export const getEpisodeInvoice=(id:string)=>apiRequest<Invoice>(`${base}/episodes/${id}/invoice`);
export const createEpisodeInvoice=(id:string,items:{tariffId:string;quantity:number}[],key:string)=>apiRequest<Invoice>(`${base}/episodes/${id}/invoice`,{method:'POST',headers:{'Idempotency-Key':key},body:JSON.stringify({items})});
export const recordCareCash=(id:string,input:{amount:number;currency:string},key:string)=>apiRequest<CashReceipt>(`${base}/invoices/${id}/cash`,{method:'POST',headers:{'Idempotency-Key':key},body:JSON.stringify(input)});
export const refundCareCash=(id:string,input:{amount:number;currency:string;reason:string},key:string)=>apiRequest<CashReceipt>(`${base}/invoices/${id}/refunds`,{method:'POST',headers:{'Idempotency-Key':key},body:JSON.stringify(input)});
export const listCashReceipts=(id:string)=>apiRequest<CashReceipt[]>(`${base}/invoices/${id}/receipts`);
