import {useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {searchPatientIdentities,identityKeys} from '../api/patientIdentities';
import {ApiErrorMessage} from '../components/ApiErrorMessage';
import {ResourceTable} from '../components/ResourceTable';
import {useAuth} from '../auth/AuthContext';
export function PatientsPage(){
 const {t,i18n}=useTranslation();const fr=i18n.language.startsWith('fr');const auth=useAuth();
 const [search,setSearch]=useState('');const [page,setPage]=useState(0);
 const result=useQuery({queryKey:[...identityKeys.all,'list',search,page],queryFn:()=>searchPatientIdentities(search,page)});
 const canWrite=auth.hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','HOSPITAL_AGENT','NURSE');
 return <section><div className="page-heading"><h1>{t('patients.title')}</h1>{canWrite&&<Link className="button" to="/patients/new">+ {t('patients.new')}</Link>}</div>
 <p>{fr?'Recherche des identités sur toute la plateforme. L’accès au dossier médical nécessite une autorisation distincte.':'Search identities across the platform. Medical records require separate authorization.'}</p>
 <div className="table-filters"><label>{t('common.search')}<input type="search" placeholder={fr?'Nom, numéro patient ou identifiant':'Name, patient number or identifier'} value={search} onChange={e=>{setSearch(e.target.value);setPage(0);}}/></label></div>
 {result.isPending&&<p>{t('common.loading')}</p>}{result.isError&&<ApiErrorMessage error={result.error}/>}
 {result.data&&<ResourceTable data={result.data} page={page} onPageChange={setPage} empty={t('patients.empty')} columns={[{label:t('patients.number'),render:row=><Link to={`/patients/${row.id}/identity`}>{row.patientNumber}</Link>},{label:fr?'Nom':'Name',render:row=>`${row.firstName} ${row.lastName}`},{label:fr?'Date de naissance':'Birth date',render:row=>row.birthDate||'—'},{label:t('patients.status'),render:row=>row.status}]} actions={row=><Link className="table-action" to={`/patients/${row.id}/identity`}>{fr?'Accueillir':'Reception'}</Link>}/>}
 </section>;
}
