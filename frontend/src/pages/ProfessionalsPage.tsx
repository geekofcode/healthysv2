import {FormEvent,useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {listProfessionals,professionalKeys} from '../api/professionals';
import {ApiErrorMessage} from '../components/ApiErrorMessage';
import {useAuth} from '../auth/AuthContext';

export function ProfessionalsPage(){const {t}=useTranslation();const auth=useAuth();const [input,setInput]=useState('');const [query,setQuery]=useState('');const result=useQuery({queryKey:professionalKeys.list(query),queryFn:()=>listProfessionals(query)});const submit=(e:FormEvent)=>{e.preventDefault();setQuery(input.trim())};const canWrite=auth.hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN');
 return <section><div className="page-heading"><h1>{t('professionals.title')}</h1>{canWrite&&<Link className="button" to="/professionals/new">{t('professionals.new')}</Link>}</div><form className="search-form" onSubmit={submit}><input aria-label={t('professionals.search')} placeholder={t('professionals.searchPlaceholder')} value={input} onChange={e=>setInput(e.target.value)}/><button>{t('professionals.search')}</button></form>{result.isPending?<p>{t('common.loading')}</p>:result.isError?<ApiErrorMessage error={result.error}/>:result.data.content.length===0?<p>{t('professionals.empty')}</p>:<table><thead><tr><th>{t('professionals.number')}</th><th>{t('professionals.type')}</th><th>{t('professionals.personId')}</th><th>{t('professionals.status')}</th></tr></thead><tbody>{result.data.content.map(p=><tr key={p.id}><td><Link to={`/professionals/${p.id}`}>{p.professionalNumber}</Link></td><td>{p.professionalType}</td><td>{p.personId}</td><td>{t(`status.${p.status}`,p.status)}</td></tr>)}</tbody></table>}</section>}
