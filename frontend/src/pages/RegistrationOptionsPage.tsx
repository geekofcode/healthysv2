import {useState, type FormEvent} from 'react';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {apiRequest} from '../api/client';
import {ApiErrorMessage} from '../components/ApiErrorMessage';

type Country={id:string;iso2:string;name:string};
type Profession={code:string;labelEn:string;labelFr:string};
type Options={countries:Country[];professions:Profession[]};
const key=['onboarding-countries'];
export function RegistrationOptionsPage(){
 const {t,i18n}=useTranslation();const client=useQueryClient();
 const query=useQuery({queryKey:key,queryFn:()=>apiRequest<Options>('/registration-options')});
 const [filter,setFilter]=useState('');const [editing,setEditing]=useState<Country|null>(null);
 const [deleting,setDeleting]=useState<Country|null>(null);const [iso2,setIso2]=useState('');const [name,setName]=useState('');
 const save=useMutation({mutationFn:()=>apiRequest(`/admin/registration-options/countries${editing?`/${editing.id}`:''}`,{method:editing?'PUT':'POST',body:JSON.stringify({iso2:iso2.trim().toUpperCase(),name:name.trim()})}),onSuccess:()=>{void client.invalidateQueries({queryKey:key});setEditing(null);setName('');setIso2('');}});
 const remove=useMutation({mutationFn:(id:string)=>apiRequest(`/admin/registration-options/countries/${id}`,{method:'DELETE'}),onSuccess:()=>{setDeleting(null);void client.invalidateQueries({queryKey:key});}});
 function submit(e:FormEvent){e.preventDefault();save.mutate();}
 const countries=query.data?.countries.filter(c=>`${c.name} ${c.iso2}`.toLocaleLowerCase().includes(filter.toLocaleLowerCase()))??[];
 return <section><h1>{t('onboarding.referenceTitle')}</h1><h2>{t('onboarding.configureCountries')}</h2><p>{t('onboarding.countryDeleteHint')}</p>
 <form className="entity-form" onSubmit={submit}><label>{t('onboarding.countryCode')}<input required pattern="[A-Za-z]{2}" maxLength={2} value={iso2} onChange={e=>setIso2(e.target.value)}/></label><label>{t('organizations.name')}<input required maxLength={150} value={name} onChange={e=>setName(e.target.value)}/></label><div className="form-actions"><button disabled={save.isPending}>{editing?t('common.save'):t('common.add')}</button>{editing&&<button type="button" className="secondary" onClick={()=>{setEditing(null);setName('');setIso2('');save.reset();}}>{t('common.cancel')}</button>}</div>{save.isError&&<ApiErrorMessage error={save.error}/>}</form>
 <div className="table-filters"><label>{t('common.search')}<input value={filter} onChange={e=>setFilter(e.target.value)}/></label></div>
 {query.isPending&&<p>{t('common.loading')}</p>}{query.isError&&<ApiErrorMessage error={query.error}/>}
 {deleting&&<div className="delete-confirmation"><p>{t('onboarding.deleteCountry',{name:deleting.name})}</p><button className="danger" disabled={remove.isPending} onClick={()=>remove.mutate(deleting.id)}>{t('common.delete')}</button> <button className="secondary" type="button" onClick={()=>{setDeleting(null);remove.reset();}}>{t('common.cancel')}</button>{remove.isError&&<ApiErrorMessage error={remove.error}/>}</div>}
 <div className="resource-table"><div className="table-scroll"><table><thead><tr><th>{t('onboarding.countryCode')}</th><th>{t('organizations.name')}</th><th>{t('common.actions')}</th></tr></thead><tbody>{countries.map(c=><tr key={c.id}><td>{c.iso2}</td><td>{c.name}</td><td><div className="row-actions"><button className="secondary" onClick={()=>{setEditing(c);setIso2(c.iso2);setName(c.name);save.reset();}}>{t('common.edit')}</button><button className="danger" onClick={()=>{setDeleting(c);remove.reset();}}>{t('common.delete')}</button></div></td></tr>)}</tbody></table></div></div>
 <h2>{t('onboarding.professionList')}</h2><p>{t('onboarding.professionListHint')}</p><div className="resource-table"><table><thead><tr><th>{t('onboarding.profession')}</th><th>{t('onboarding.professionRole')}</th></tr></thead><tbody>{query.data?.professions.map(p=><tr key={p.code}><td>{i18n.language.startsWith('fr')?p.labelFr:p.labelEn}</td><td>{p.code}</td></tr>)}</tbody></table></div>
 </section>;
}
