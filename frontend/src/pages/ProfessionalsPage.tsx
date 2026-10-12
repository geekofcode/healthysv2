import {useState} from 'react';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {listProfessionals,deleteProfessional,professionalKeys} from '../api/professionals';
import {ApiErrorMessage} from '../components/ApiErrorMessage';
import {ResourceTable} from '../components/ResourceTable';
import {useAuth} from '../auth/AuthContext';
export function ProfessionalsPage(){
 const {t,i18n}=useTranslation();const fr=i18n.language.startsWith('fr');const auth=useAuth();const client=useQueryClient();
 const [search,setSearch]=useState('');const [page,setPage]=useState(0);const [removeId,setRemoveId]=useState('');
 const result=useQuery({queryKey:[...professionalKeys.all,'list',search,page],queryFn:()=>listProfessionals(search,page,20)});
 const removal=useMutation({mutationFn:deleteProfessional,onSuccess:async()=>{setRemoveId('');await client.invalidateQueries({queryKey:professionalKeys.all});}});
 const canWrite=auth.hasAnyRole('PLATFORM_ADMIN');
 return <section><div className="page-heading"><h1>{t('professionals.title')}</h1>{canWrite&&<Link className="button" to="/professionals/new">+ {t('professionals.new')}</Link>}</div>
 <div className="table-filters"><label>{t('common.search')}<input type="search" placeholder={t('professionals.searchPlaceholder')} value={search} onChange={e=>{setSearch(e.target.value);setPage(0);}}/></label></div>
 {result.isPending&&<p>{t('common.loading')}</p>}{result.isError&&<ApiErrorMessage error={result.error}/>}{removal.isError&&<ApiErrorMessage error={removal.error}/>}
 {removeId&&<div className="delete-confirmation" role="alert"><p>{fr?'Confirmer la suppression de cette fiche ?':'Confirm deletion of this record?'}</p><button className="danger" disabled={removal.isPending} onClick={()=>removal.mutate(removeId)}>{t('common.delete')}</button><button className="secondary" onClick={()=>setRemoveId('')}>{t('common.cancel')}</button></div>}
 {result.data&&<ResourceTable data={result.data} page={page} onPageChange={setPage} empty={t('professionals.empty')} columns={[{label:t('professionals.number'),render:row=><Link to={`/professionals/${row.id}`}>{row.professionalNumber}</Link>},{label:t('professionals.type'),render:row=>t(`professionalTypes.${row.professionalType}`,row.professionalType)},{label:t('professionals.status'),render:row=><span className={`status-badge status-${row.status.toLowerCase()}`}>{t(`status.${row.status}`,row.status)}</span>}]} actions={row=><><Link className="table-action" to={`/professionals/${row.id}`}>{fr?'Voir':'View'}</Link>{canWrite&&<><Link className="table-action" to={`/professionals/${row.id}/edit`}>{t('common.edit')}</Link><button className="table-action danger" onClick={()=>setRemoveId(row.id)}>{t('common.delete')}</button></>}</>}/>}
 </section>;
}
