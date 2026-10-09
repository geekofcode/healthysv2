import {useState} from 'react';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {listPatients,deletePatient,patientKeys} from '../api/patients';
import {ApiErrorMessage} from '../components/ApiErrorMessage';
import {ResourceTable} from '../components/ResourceTable';
import {useAuth} from '../auth/AuthContext';
export function PatientsPage(){
 const {t,i18n}=useTranslation();const fr=i18n.language.startsWith('fr');const auth=useAuth();const client=useQueryClient();
 const [search,setSearch]=useState('');const [page,setPage]=useState(0);const [removeId,setRemoveId]=useState('');
 const result=useQuery({queryKey:[...patientKeys.all,'list',search,page],queryFn:()=>listPatients(search,page,20)});
 const removal=useMutation({mutationFn:deletePatient,onSuccess:async()=>{setRemoveId('');await client.invalidateQueries({queryKey:patientKeys.all});}});
 const canWrite=auth.hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','HOSPITAL_AGENT');
 return <section><div className="page-heading"><h1>{t('patients.title')}</h1>{canWrite&&<Link className="button" to="/patients/new">+ {t('patients.new')}</Link>}</div>
 <div className="table-filters"><label>{t('common.search')}<input type="search" placeholder={t('patients.searchPlaceholder')} value={search} onChange={e=>{setSearch(e.target.value);setPage(0);}}/></label></div>
 {result.isPending&&<p>{t('common.loading')}</p>}{result.isError&&<ApiErrorMessage error={result.error}/>}{removal.isError&&<ApiErrorMessage error={removal.error}/>}
 {removeId&&<div className="delete-confirmation" role="alert"><p>{fr?'Confirmer la suppression de cette fiche ?':'Confirm deletion of this record?'}</p><button className="danger" disabled={removal.isPending} onClick={()=>removal.mutate(removeId)}>{t('common.delete')}</button><button className="secondary" onClick={()=>setRemoveId('')}>{t('common.cancel')}</button></div>}
 {result.data&&<ResourceTable data={result.data} page={page} onPageChange={setPage} empty={t('patients.empty')} columns={[{label:t('patients.number'),render:row=><Link to={`/patients/${row.id}`}>{row.patientNumber}</Link>},{label:t('patients.bloodGroup'),render:row=>row.bloodGroup||'—'},{label:t('patients.status'),render:row=><span className={`status-badge status-${row.status.toLowerCase()}`}>{t(`status.${row.status}`,row.status)}</span>}]} actions={row=><><Link className="table-action" to={`/patients/${row.id}`}>{fr?'Voir':'View'}</Link>{canWrite&&<><Link className="table-action" to={`/patients/${row.id}/edit`}>{t('common.edit')}</Link><button className="table-action danger" onClick={()=>setRemoveId(row.id)}>{t('common.delete')}</button></>}</>}/>}
 </section>;
}
