import {FormEvent,useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {listPatients,patientKeys} from '../api/patients';
import {ApiErrorMessage} from '../components/ApiErrorMessage';
import {useAuth} from '../auth/AuthContext';

export function PatientsPage(){const {t}=useTranslation();const auth=useAuth();const [draft,setDraft]=useState('');const [query,setQuery]=useState('');const result=useQuery({queryKey:patientKeys.list(query),queryFn:()=>listPatients(query)});const submit=(e:FormEvent)=>{e.preventDefault();setQuery(draft.trim())};const canCreate=auth.hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','HOSPITAL_AGENT');return <section><div className="page-heading"><div><p className="eyebrow">{t('patients.eyebrow')}</p><h1>{t('patients.title')}</h1></div>{canCreate&&<Link className="button-link" to="/patients/new">{t('patients.new')}</Link>}</div><form className="search-bar" onSubmit={submit}><input value={draft} onChange={e=>setDraft(e.target.value)} placeholder={t('patients.searchPlaceholder')}/><button>{t('patients.search')}</button></form>{result.isPending&&<p>{t('common.loading')}</p>}{result.isError&&<ApiErrorMessage error={result.error}/>}<div className="card-grid">{result.data?.content.map(p=><Link className="entity-card" key={p.id} to={`/patients/${p.id}`}><strong>{p.patientNumber}</strong><span>{p.bloodGroup||'—'} {p.rhesus||''}</span><small>{t(`status.${p.status}`,p.status)}</small></Link>)}</div>{result.data&&!result.data.content.length&&<p>{t('patients.empty')}</p>}</section>}
