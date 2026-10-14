import {useHospitalPermission} from '../auth/HospitalPermissions';
import {getCareCutover} from '../api/cutover';
import {useState,type FormEvent} from 'react';
import {Link,useParams} from 'react-router-dom';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {billingKeys,cancelInvoice,getInvoice,issueInvoice} from '../api/billing';
import {listCashReceipts,recordCareCash,refundCareCash,type CashReceipt} from '../api/careBilling';
import {useAuth} from '../auth/AuthContext';
import {ApiErrorMessage} from '../components/ApiErrorMessage';

export function InvoiceDetailPage(){
 const invoiceUpdate=useHospitalPermission('invoices','UPDATE');const paymentCreate=useHospitalPermission('payments','CREATE');const refundCreate=useHospitalPermission('refunds','CREATE');
 const {id=''}=useParams();const {t,i18n}=useTranslation();const fr=i18n.language.startsWith('fr');const auth=useAuth();const cache=useQueryClient();
 const invoice=useQuery({queryKey:billingKeys.invoice(id),queryFn:()=>getInvoice(id),enabled:!!id});
 const cutover=useQuery({queryKey:['care','cutover'],queryFn:getCareCutover,enabled:!!invoice.data&&!invoice.data.careEpisodeId,staleTime:0,refetchInterval:30000});
 const [amount,setAmount]=useState('');const [refundAmount,setRefundAmount]=useState('');const [reason,setReason]=useState('');
 const [paymentKey,setPaymentKey]=useState(()=>crypto.randomUUID());const [refundKey,setRefundKey]=useState(()=>crypto.randomUUID());const [receipt,setReceipt]=useState<CashReceipt>();
 const receipts=useQuery({queryKey:['cash-receipts',id],queryFn:()=>listCashReceipts(id),enabled:!!invoice.data?.careEpisodeId});
 const refresh=()=>{void cache.invalidateQueries({queryKey:billingKeys.invoice(id)});void cache.invalidateQueries({queryKey:['billing','invoices']});void cache.invalidateQueries({queryKey:['cash-receipts',id]});void cache.invalidateQueries({queryKey:['care-episodes']});};
 const issue=useMutation({mutationFn:()=>issueInvoice(id),onSuccess:refresh});const cancel=useMutation({mutationFn:()=>cancelInvoice(id),onSuccess:refresh});
 const pay=useMutation({mutationFn:async()=>{const input={amount:Number(amount),currency:invoice.data!.currency};setReceipt(await recordCareCash(id,input,paymentKey));},onSuccess:()=>{setAmount('');setPaymentKey(crypto.randomUUID());refresh();}});
 const refund=useMutation({mutationFn:()=>refundCareCash(id,{amount:Number(refundAmount),currency:invoice.data!.currency,reason},refundKey),onSuccess:result=>{setReceipt(result);setRefundAmount('');setReason('');setRefundKey(crypto.randomUUID());refresh();}});
 if(invoice.isPending)return <p>{t('common.loading')}</p>;
 if(invoice.isError)return <ApiErrorMessage error={invoice.error}/>;
 const data=invoice.data;const canWrite=invoiceUpdate&&auth.hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','HOSPITAL_AGENT','NURSE','ACCOUNTANT');const canCash=auth.hasAnyRole('CASHIER');
 const payable=['ISSUED','PARTIALLY_PAID'].includes(data.status);const money=(value:number)=>new Intl.NumberFormat(i18n.language,{style:'currency',currency:data.currency}).format(value);
 const submit=(event:FormEvent)=>{event.preventDefault();if(Number(amount)>0)pay.mutate();};
 const error=issue.error||cancel.error||pay.error||refund.error;
 return <section className="invoice-print">
  <Link className="no-print" to="/billing">← {t('common.back')}</Link>
  <div className="page-heading"><div><p className="eyebrow">{t('billing.invoice')}</p><h1>{data.invoiceNumber}</h1>{data.careEpisodeId&&auth.hasAnyRole('NURSE','DOCTOR','HOSPITAL_ADMIN','HOSPITAL_AGENT','PLATFORM_ADMIN')&&<Link className="no-print" to={`/care/episodes/${data.careEpisodeId}`}>{fr?'Voir la prise en charge':'View care episode'}</Link>}</div><span className="status-badge">{t(`status.${data.status}`,data.status)}</span><button className="no-print" onClick={()=>{setReceipt(undefined);requestAnimationFrame(()=>window.print());}}>{fr?'Imprimer la facture':'Print invoice'}</button></div>
  <dl className="profile-grid">
   <div><dt>{t('billing.patientId')}</dt><dd>{data.patientId}</dd></div><div><dt>{t('billing.organizationId')}</dt><dd>{data.organizationId}</dd></div><div><dt>{t('billing.total')}</dt><dd>{money(data.totalAmount)}</dd></div><div><dt>{t('billing.amountPaid')}</dt><dd>{money(data.amountPaid)}</dd></div><div><dt>{t('billing.balance')}</dt><dd>{money(data.balance)}</dd></div><div><dt>{t('billing.dueAt')}</dt><dd>{data.dueAt?new Date(data.dueAt).toLocaleString(i18n.language):'—'}</dd></div>
  </dl>
  {canWrite&&!data.careEpisodeId&&cutover.data&&cutover.data.mode!=='CARE_ONLY'&&<div className="card-actions billing-actions">{data.status==='DRAFT'&&<button onClick={()=>issue.mutate()} disabled={issue.isPending}>{t('billing.issue')}</button>}{['DRAFT','ISSUED'].includes(data.status)&&data.amountPaid===0&&<button className="danger" onClick={()=>cancel.mutate()} disabled={cancel.isPending}>{t('billing.cancelInvoice')}</button>}</div>}
  <section className="detail-section"><h2>{t('billing.items')}</h2><div className="table-scroll"><table><thead><tr><th>{t('billing.description')}</th><th>{t('billing.quantity')}</th><th>{t('billing.unitPrice')}</th><th>{t('billing.taxAmount')}</th><th>{t('billing.total')}</th></tr></thead><tbody>{data.items.map(item=><tr key={item.id}><td>{item.description}</td><td>{item.quantity}</td><td>{money(item.unitPrice)}</td><td>{money(item.taxAmount)}</td><td>{money(item.totalAmount)}</td></tr>)}</tbody></table></div></section>
  {canCash&&paymentCreate&&data.careEpisodeId&&payable&&<section className="detail-section no-print"><h2>{t('billing.recordPayment')}</h2><form className="entity-form payment-form" onSubmit={submit}><label>{t('billing.amount')}<input required type="number" min="0.01" max={data.balance} step="0.01" value={amount} onChange={e=>{setAmount(e.target.value);setPaymentKey(crypto.randomUUID());}}/></label><p>{t('billing.paymentMethod')}: {t('billing.methods.CASH')}</p><button disabled={pay.isPending}>{t('billing.confirmPayment')}</button></form></section>}
  <section className="detail-section"><h2>{t('billing.payments')}</h2><div className="payment-list">{data.payments.map(payment=><article className="payment-card" key={payment.id}><strong>{payment.paymentNumber}</strong><span>{t(`billing.methods.${payment.paymentMethod}`,payment.paymentMethod)}</span><b>{money(payment.amount)}</b><small>{new Date(payment.paidAt).toLocaleString(i18n.language)}</small></article>)}</div>{!data.payments.length&&<p>{t('billing.noPayments')}</p>}</section>
  {canCash&&refundCreate&&data.careEpisodeId&&data.amountPaid>0&&<form className="entity-form no-print" onSubmit={e=>{e.preventDefault();refund.mutate();}}><h2>{fr?'Restitution cash':'Cash refund'}</h2><p>{fr?'Restitution possible avant la première rencontre médicale. Un motif est obligatoire.':'Refunds are available before the first medical encounter. A reason is required.'}</p><label>{fr?'Montant à restituer':'Refund amount'}<input required type="number" min="0.01" max={data.amountPaid} step="0.01" value={refundAmount} onChange={e=>{setRefundAmount(e.target.value);setRefundKey(crypto.randomUUID());}}/></label><label>{fr?'Motif de restitution':'Refund reason'}<textarea required maxLength={500} value={reason} onChange={e=>{setReason(e.target.value);setRefundKey(crypto.randomUUID());}}/></label><button disabled={refund.isPending}>{fr?'Confirmer la restitution':'Confirm refund'}</button></form>}
  {receipts.data&&<section className="no-print"><h2>{fr?'Reçus et restitutions':'Receipts and refunds'}</h2>{receipts.data.map(item=><p key={item.id}><button onClick={()=>setReceipt(item)}>{item.receiptNumber}</button> · {item.operationType} · {money(item.amount)}</p>)}</section>}
  {receipt&&<section className="cash-receipt" role="status"><h2>{receipt.receiptNumber}</h2><p>{receipt.operationType==='REFUND'?(fr?'Restitution en espèces':'Cash refund'):(fr?'Reçu de paiement en espèces':'Cash payment receipt')}</p><p>{data.invoiceNumber} · {money(receipt.amount)}</p><p>{new Date(receipt.occurredAt).toLocaleString(i18n.language)}</p>{receipt.reason&&<p>{receipt.reason}</p>}<button className="no-print" onClick={()=>window.print()}>{fr?'Imprimer le reçu':'Print receipt'}</button></section>}
  {receipts.error&&<ApiErrorMessage error={receipts.error}/>} {error&&<ApiErrorMessage error={error}/>}
 </section>;
}
