import {useState} from 'react';
import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {listPatients} from '../api/patients';
import {listLabExams} from '../api/laboratory';
import {listProfessionals} from '../api/professionals';
import {listOrganizations} from '../api/organizations';
import {ApiErrorMessage} from './ApiErrorMessage';

export function ClinicalReferenceSelect({kind,value,onChange,required=false,label}:{kind:'patient'|'organization'|'professional'|'exam';value:string;onChange:(value:string)=>void;required?:boolean;label:string}){
 const {i18n}=useTranslation();const fr=i18n.language.startsWith('fr');const [search,setSearch]=useState('');const [page,setPage]=useState(0);
 const result=useQuery({queryKey:['reference-selection',kind,search,page],queryFn:async()=>kind==='patient'?listPatients(search,page,20).then(data=>({...data,content:data.content.map(p=>({id:p.id,name:p.patientNumber}))})):kind==='exam'?listLabExams(search,page).then(data=>({...data,content:data.content.map(exam=>({id:exam.id,name:`${exam.code} — ${exam.name}`}))})):kind==='professional'?listProfessionals(search,page,20).then(data=>({...data,content:data.content.map(p=>({id:p.id,name:p.professionalNumber}))})):listOrganizations(search,page,20).then(data=>({...data,content:data.content.map(o=>({id:o.id,name:o.name}))}))});
 return <div className="relation-picker"><label>{label}<select aria-label={label} required={required} value={value} onChange={e=>onChange(e.target.value)}><option value="">—</option>{result.data?.content.map(item=><option key={item.id} value={item.id}>{item.name}</option>)}</select></label><input type="search" aria-label={`${label} ${fr?'recherche':'search'}`} placeholder={fr?'Rechercher…':'Search…'} value={search} onChange={e=>{setSearch(e.target.value);setPage(0)}}/>{result.data&&result.data.page.totalPages>1&&<div className="inline-actions"><button className="secondary" type="button" disabled={page===0} onClick={()=>setPage(page-1)}>‹</button><span>{page+1} / {result.data.page.totalPages}</span><button className="secondary" type="button" disabled={result.data.page.last} onClick={()=>setPage(page+1)}>›</button></div>}{result.isError&&<ApiErrorMessage error={result.error}/>}</div>;
}
