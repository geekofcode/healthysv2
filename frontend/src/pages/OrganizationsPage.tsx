import {useState} from 'react';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {deleteOrganization,listOrganizations,organizationKeys} from '../api/organizations';
import {useAuth} from '../auth/AuthContext';
import {ApiErrorMessage} from '../components/ApiErrorMessage';
import {ResourceTable} from '../components/ResourceTable';
export function OrganizationsPage(){
 const {t,i18n}=useTranslation();const fr=i18n.language.startsWith('fr');const auth=useAuth();const client=useQueryClient();
 const [search,setSearch]=useState('');const [status,setStatus]=useState('');const [page,setPage]=useState(0);const [pendingDelete,setPendingDelete]=useState<{id:string;name:string}>();
 const query=useQuery({queryKey:[...organizationKeys.all,'list',search,status,page],queryFn:()=>listOrganizations(search,page,20,status)});
 const removal=useMutation({mutationFn:deleteOrganization,onSuccess:async()=>{setPendingDelete(undefined);await client.invalidateQueries({queryKey:organizationKeys.all});}});
 const canWrite=auth.hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN');
 return <section><div className="page-heading"><div><p className="eyebrow">{fr?'Établissements de santé':'Health organizations'}</p><h1>{t('organizations.title')}</h1></div>{canWrite&&<Link className="button" to="/organizations/new">+ {t('organizations.new')}</Link>}</div>
 <div className="table-filters"><label>{t('common.search')}<input type="search" value={search} placeholder={fr?'Nom ou numéro…':'Name or number…'} onChange={e=>{setSearch(e.target.value);setPage(0);}}/></label><label>{t('organizations.status')}<select value={status} onChange={e=>{setStatus(e.target.value);setPage(0);}}><option value="">{fr?'Tous les statuts':'All statuses'}</option>{['ACTIVE','INACTIVE'].map(s=><option key={s} value={s}>{t(`status.${s}`,s)}</option>)}</select></label></div>
 {query.isPending&&<p>{t('organizations.loading')}</p>}{query.isError&&<ApiErrorMessage error={query.error}/>}{removal.isError&&<ApiErrorMessage error={removal.error}/>}
 {pendingDelete&&<div className="delete-confirmation" role="alert"><p>{fr?'Supprimer':'Delete'} <strong>{pendingDelete.name}</strong> ?</p><button className="danger" disabled={removal.isPending} onClick={()=>removal.mutate(pendingDelete.id)}>{t('common.delete')}</button><button className="secondary" onClick={()=>setPendingDelete(undefined)}>{t('common.cancel')}</button></div>}
 {query.data&&<ResourceTable data={query.data} page={page} onPageChange={setPage} empty={t('organizations.empty')} columns={[{label:t('organizations.number'),render:o=><Link to={`/organizations/${o.id}`}>{o.number}</Link>},{label:t('organizations.name'),render:o=>o.name},{label:t('organizations.status'),render:o=><span className={`status-badge status-${o.status.toLowerCase()}`}>{t(`status.${o.status}`,o.status)}</span>}]} actions={o=><><Link className="table-action" to={`/organizations/${o.id}`}>{fr?'Voir':'View'}</Link>{canWrite&&<><Link className="table-action" to={`/organizations/${o.id}/edit`}>{t('common.edit')}</Link><button type="button" className="table-action danger" onClick={()=>setPendingDelete(o)}>{t('common.delete')}</button></>}</>}/>}
 </section>;
}
