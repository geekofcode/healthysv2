import {beforeEach,expect,it,vi} from 'vitest';
vi.mock('./client',()=>({apiRequest:vi.fn()}));
import {apiRequest} from './client';
import {createEpisodeInvoice,recordCareCash,refundCareCash,updateCareTariff} from './careBilling';
beforeEach(()=>vi.mocked(apiRequest).mockReset());
it('creates episode invoice from tariff references and retains retry key',()=>{
 createEpisodeInvoice('e',[{tariffId:'tariff',quantity:2}],'retry-key');
 expect(apiRequest).toHaveBeenCalledWith('/billing/care/episodes/e/invoice',{method:'POST',headers:{'Idempotency-Key':'retry-key'},body:JSON.stringify({items:[{tariffId:'tariff',quantity:2}]})});
});
it('cash operations use idempotency and refunds retain reason',()=>{
 recordCareCash('i',{amount:10,currency:'CAD'},'pay');
 expect(apiRequest).toHaveBeenLastCalledWith('/billing/care/invoices/i/cash',{method:'POST',headers:{'Idempotency-Key':'pay'},body:JSON.stringify({amount:10,currency:'CAD'})});
 refundCareCash('i',{amount:2,currency:'CAD',reason:'Annulation'},'refund');
 expect(apiRequest).toHaveBeenLastCalledWith('/billing/care/invoices/i/refunds',{method:'POST',headers:{'Idempotency-Key':'refund'},body:JSON.stringify({amount:2,currency:'CAD',reason:'Annulation'})});
});
it('tariff edits preserve concurrency version',()=>{
 const input={organizationId:'o',code:'CONS',description:'Consultation',currency:'CAD',unitPrice:20,taxRate:0,active:false,version:3};
 updateCareTariff('t',input);
 expect(apiRequest).toHaveBeenCalledWith('/billing/care/tariffs/t',{method:'PUT',body:JSON.stringify(input)});
});
