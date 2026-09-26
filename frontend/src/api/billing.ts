import {apiRequest} from './client';
import type {Page} from './organizations';

export type InvoiceStatus='DRAFT'|'ISSUED'|'PARTIALLY_PAID'|'PAID'|'CANCELLED';
export type InvoiceItemInput={itemType:string;referenceId?:string;description:string;quantity:number;unitPrice:number;taxAmount:number};
export type InvoiceInput={patientId:string;organizationId:string;encounterId?:string;currency:string;dueAt?:string;items:InvoiceItemInput[]};
export type InvoiceItem=InvoiceItemInput&{id:string;totalAmount:number};
export type PaymentTransaction={id:string;provider?:string;externalTransactionId?:string;transactionType:string;amount:number;status:string;occurredAt:string};
export type Payment={id:string;paymentNumber:string;invoiceId:string;amount:number;currency:string;paymentMethod:string;paidAt:string;status:string;transactions:PaymentTransaction[]};
export type InvoiceSummary={id:string;invoiceNumber:string;patientId:string;organizationId:string;currency:string;totalAmount:number;amountPaid:number;balance:number;issuedAt:string;dueAt?:string;status:InvoiceStatus};
export type Invoice=InvoiceSummary&{encounterId?:string;subtotal:number;taxAmount:number;items:InvoiceItem[];payments:Payment[]};
export type InvoiceFilters={patientId?:string;organizationId?:string;status?:string};
export type PaymentInput={amount:number;currency:string;paymentMethod:string;provider?:string;externalTransactionId?:string};

const query=(values:Record<string,string|undefined>)=>{const params=new URLSearchParams();Object.entries(values).forEach(([key,value])=>{if(value?.trim())params.set(key,value.trim())});return params.size?`?${params}`:''};
export const billingKeys={invoices:(filters:InvoiceFilters)=>['billing','invoices',filters] as const,invoice:(id:string)=>['billing','invoice',id] as const};
export const listInvoices=(filters:InvoiceFilters)=>apiRequest<Page<InvoiceSummary>>(`/invoices${query({...filters,size:'50',sort:'issuedAt,desc'})}`);
export const getInvoice=(id:string)=>apiRequest<Invoice>(`/invoices/${id}`);
export const createInvoice=(input:InvoiceInput)=>apiRequest<Invoice>('/invoices',{method:'POST',body:JSON.stringify(input)});
export const issueInvoice=(id:string)=>apiRequest<Invoice>(`/invoices/${id}/issue`,{method:'POST'});
export const cancelInvoice=(id:string)=>apiRequest<Invoice>(`/invoices/${id}/cancel`,{method:'POST'});
export const recordPayment=(id:string,input:PaymentInput)=>apiRequest<Payment>(`/invoices/${id}/payments`,{method:'POST',body:JSON.stringify(input)});
export const validInvoiceItem=(item:InvoiceItemInput)=>Boolean(item.itemType.trim()&&item.description.trim()&&item.quantity>0&&item.unitPrice>=0&&item.taxAmount>=0);
