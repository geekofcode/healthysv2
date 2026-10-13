import {useState} from 'react';
import {Link} from 'react-router-dom';
import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {useAuth} from '../auth/AuthContext';
import {BillingOrganizationSelect} from '../components/BillingOrganizationSelect';
import {ResourceTable} from '../components/ResourceTable';
import {ApiErrorMessage} from '../components/ApiErrorMessage';
import {billingKeys,listInvoices,type InvoiceFilters} from '../api/billing';

export function BillingPage(){
 const {t,i18n}=useTranslation();const fr=i18n.language.startsWith('fr');const auth=useAuth();
 const [page,setPage]=useState(0);const [filters,setFilters]=useState<InvoiceFilters>({});const [applied,setApplied]=useState<InvoiceFilters>({});
 const invoices=useQuery({queryKey:[...billingKeys.invoices(applied),page],queryFn:()=>listInvoices(applied,page)});
 const staff=auth.hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','HOSPITAL_AGENT','NURSE','CASHIER','ACCOUNTANT');
 const reception=auth.hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','HOSPITAL_AGENT','NURSE');
 const money=(amount:number,currency:string)=>new Intl.NumberFormat(i18n.language,{style:'currency',currency}).format(amount);
 return <section>
  <div className="page-heading"><div><p className="eyebrow">{t('billing.eyebrow')}</p><h1>{t('billing.title')}</h1></div><div className="card-actions">{staff&&<Link to="/billing/tariffs">{fr?'Tarifs de soins':'Care tariffs'}</Link>}{reception&&<Link to="/patients">{fr?'Accueillir un patient':'Receive patient'}</Link>}</div></div>
  {reception&&<p>{fr?'La facture est générée depuis la prise en charge du patient, à partir des tarifs de l’établissement.':'Invoices are generated from the patient’s care episode using organization tariffs.'}</p>}
  <form className="search-form" onSubmit={event=>{event.preventDefault();setApplied(filters);setPage(0);}}>

   {staff&&<BillingOrganizationSelect label={t('billing.organizationId')} value={filters.organizationId||''} onChange={organizationId=>setFilters({...filters,organizationId})}/>}
   <select aria-label={t('common.status')} value={filters.status||''} onChange={event=>setFilters({...filters,status:event.target.value})}><option value="">{t('billing.allStatuses')}</option>{['DRAFT','ISSUED','PARTIALLY_PAID','PAID','CANCELLED'].map(status=><option value={status} key={status}>{t(`status.${status}`,status)}</option>)}</select><button>{t('common.search')}</button>
  </form>
  {invoices.isPending&&<p>{t('common.loading')}</p>}{invoices.isError&&<ApiErrorMessage error={invoices.error}/>}
  {invoices.data&&<ResourceTable data={invoices.data} page={page} onPageChange={setPage} empty={t('billing.empty')} columns={[
   {label:t('billing.item'),render:invoice=><Link to={`/billing/invoices/${invoice.id}`}>{invoice.invoiceNumber}</Link>},
   {label:t('billing.patientId'),render:invoice=>invoice.patientId},
   {label:t('common.status'),render:invoice=><span className="status-badge">{t(`status.${invoice.status}`,invoice.status)}</span>},
   {label:t('billing.total'),render:invoice=>money(invoice.totalAmount,invoice.currency)},
   {label:t('billing.balance'),render:invoice=>money(invoice.balance,invoice.currency)},
  ]} actions={invoice=><Link to={`/billing/invoices/${invoice.id}`}>{t('common.view')}</Link>}/>}
 </section>;
}
