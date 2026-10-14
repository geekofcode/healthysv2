import {useHospitalPermission} from '../auth/HospitalPermissions';
import {useState} from 'react';
import {Link} from 'react-router-dom';
import {useQuery,useMutation,useQueryClient} from '@tanstack/react-query';
import {createEpisodeInvoice,getEpisodeInvoice,listCareTariffs} from '../api/careBilling';
import {ApiError,} from '../api/client';
import {ApiErrorMessage} from './ApiErrorMessage';
import {useAuth} from '../auth/AuthContext';
export function EpisodeInvoice({id,organizationId,fr}:{id:string;organizationId:string;fr:boolean}){
 const canCreate=useHospitalPermission('invoices','CREATE');const auth=useAuth();const cache=useQueryClient();const [key,setKey]=useState(()=>crypto.randomUUID());
 const [items,setItems]=useState([{tariffId:'',quantity:1}]);
 const invoice=useQuery({queryKey:['episode-invoice',id],queryFn:()=>getEpisodeInvoice(id),retry:false});
 const tariffs=useQuery({queryKey:['care-tariffs',organizationId],queryFn:()=>listCareTariffs(organizationId),enabled:auth.hasAnyRole('NURSE','HOSPITAL_ADMIN','HOSPITAL_AGENT','ACCOUNTANT','PLATFORM_ADMIN')});
 const create=useMutation({mutationFn:()=>createEpisodeInvoice(id,items,key),onSuccess:()=>{setKey(crypto.randomUUID());void cache.invalidateQueries({queryKey:['episode-invoice',id]});void cache.invalidateQueries({queryKey:['care-episodes',id]});}});
 const missing=invoice.error instanceof ApiError&&invoice.error.status===404;
 if(invoice.isPending)return <p>…</p>;
 return <section className="detail-section"><h2>{fr?'Facturation de l’épisode':'Episode billing'}</h2>
  {invoice.data&&<><Link to={`/billing/invoices/${invoice.data.id}`}>{invoice.data.invoiceNumber}</Link><p>{fr?'Solde à régler':'Outstanding balance'}: {invoice.data.balance} {invoice.data.currency} · {invoice.data.status}</p></>}
  {invoice.isError&&!missing&&<ApiErrorMessage error={invoice.error}/>}
  {missing&&canCreate&&auth.hasAnyRole('NURSE','HOSPITAL_ADMIN','HOSPITAL_AGENT','ACCOUNTANT','PLATFORM_ADMIN')&&<form className="entity-form" onSubmit={e=>{e.preventDefault();create.mutate();}}>
   {items.map((item,index)=><div className="form-grid" key={index}>
    <label>{fr?'Prestation':'Service'}<select required value={item.tariffId} onChange={e=>{setKey(crypto.randomUUID());setItems(items.map((line,i)=>i===index?{...line,tariffId:e.target.value}:line));}}><option value="">—</option>{tariffs.data?.filter(t=>t.active).map(t=><option key={t.id} value={t.id}>{t.description} — {t.unitPrice} {t.currency}</option>)}</select></label>
    <label>{fr?'Quantité':'Quantity'}<input required type="number" min="0.001" step="0.001" value={item.quantity} onChange={e=>{setKey(crypto.randomUUID());setItems(items.map((line,i)=>i===index?{...line,quantity:Number(e.target.value)}:line));}}/></label>
    {items.length>1&&<button type="button" onClick={()=>{setKey(crypto.randomUUID());setItems(items.filter((_,i)=>i!==index));}}>{fr?'Retirer':'Remove'}</button>}
   </div>)}
   <button type="button" onClick={()=>{setKey(crypto.randomUUID());setItems([...items,{tariffId:'',quantity:1}]);}}>{fr?'Ajouter une ligne':'Add line'}</button>
   <p>{fr?'Le total et les taxes sont calculés par le serveur. Les prix sont conservés sur la facture.':'The server calculates totals and taxes. Invoice prices are preserved.'}</p>
   <button disabled={create.isPending||!items.every(item=>item.tariffId&&item.quantity>0)}>{fr?'Générer la facture':'Generate invoice'}</button>
   {[tariffs.error,create.error].filter(Boolean).map((error,index)=><ApiErrorMessage key={index} error={error}/>)}
  </form>}
 </section>;
}
