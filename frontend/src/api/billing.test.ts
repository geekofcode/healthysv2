import {beforeEach,describe,expect,it,vi} from 'vitest';
vi.mock('./client',()=>({apiRequest:vi.fn()}));
import {apiRequest} from './client';
import {listInvoices,recordPayment,validInvoiceItem} from './billing';
describe('billing API',()=>{beforeEach(()=>vi.mocked(apiRequest).mockReset());it('serializes invoice filters',()=>{listInvoices({patientId:'patient-1',status:'ISSUED'});expect(apiRequest).toHaveBeenCalledWith('/invoices?patientId=patient-1&status=ISSUED&size=20&page=0&sort=issuedAt%2Cdesc')});it('records payments',()=>{recordPayment('invoice-1',{amount:25,currency:'CAD',paymentMethod:'CARD'});expect(apiRequest).toHaveBeenCalledWith('/invoices/invoice-1/payments',{method:'POST',body:JSON.stringify({amount:25,currency:'CAD',paymentMethod:'CARD'})})});it('validates invoice lines',()=>{expect(validInvoiceItem({itemType:'SERVICE',description:'Care',quantity:1,unitPrice:20,taxAmount:0})).toBe(true);expect(validInvoiceItem({itemType:'',description:'Care',quantity:1,unitPrice:20,taxAmount:0})).toBe(false)})});

it('retains invoice filters when loading another server page',()=>{listInvoices({patientId:'p',status:'PAID'},2);expect(apiRequest).toHaveBeenLastCalledWith('/invoices?patientId=p&status=PAID&size=20&page=2&sort=issuedAt%2Cdesc')});
