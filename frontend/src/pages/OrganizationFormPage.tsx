import {FormEvent,useState} from 'react';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {useNavigate,useParams} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {createOrganization,getOrganization,organizationKeys,OrganizationInput,updateOrganization} from '../api/organizations';
import {ApiErrorMessage} from '../components/ApiErrorMessage';

export function OrganizationFormPage(){
 const {t}=useTranslation(); const {id}=useParams(); const navigate=useNavigate(); const client=useQueryClient();
 const query=useQuery({queryKey:id?organizationKeys.detail(id):['organization','new'],queryFn:()=>getOrganization(id!),enabled:Boolean(id)});
 const [values,setValues]=useState<OrganizationInput>({number:'',name:'',status:'ACTIVE'}); const [loaded,setLoaded]=useState(false); const [errors,setErrors]=useState<Record<string,string>>({});
 if(id&&query.data&&!loaded){setValues(query.data);setLoaded(true);}
 const mutation=useMutation({mutationFn:(input:OrganizationInput)=>id?updateOrganization(id,input):createOrganization(input),onSuccess:async result=>{await client.invalidateQueries({queryKey:organizationKeys.all});navigate(`/organizations/${result.id}`);}});
 const submit=(event:FormEvent)=>{event.preventDefault();const next:Record<string,string>={};if(!values.number&&!id)next.number=t('organizations.required');if(!values.name)next.name=t('organizations.required');if(values.email&&!/^[^@]+@[^@]+\.[^@]+$/.test(values.email))next.email=t('organizations.invalidEmail');if(values.website){try{new URL(values.website);}catch{next.website=t('organizations.invalidUrl');}}setErrors(next);if(Object.keys(next).length===0)mutation.mutate(values);};
 if(id&&query.isPending)return <p>{t('common.loading')}</p>;
 return <section><h1>{t(id?'organizations.edit':'organizations.create')}</h1>{mutation.isError&&<ApiErrorMessage error={mutation.error}/>}<form className="entity-form" onSubmit={submit} noValidate>
  {!id&&<Field label={t('organizations.number')} value={values.number} error={errors.number} onChange={number=>setValues({...values,number})}/>}<Field label={t('organizations.name')} value={values.name} error={errors.name} onChange={name=>setValues({...values,name})}/><Field label={t('organizations.legalName')} value={values.legalName??''} onChange={legalName=>setValues({...values,legalName})}/><Field label={t('organizations.phone')} value={values.phone??''} onChange={phone=>setValues({...values,phone})}/><Field label={t('organizations.email')} value={values.email??''} error={errors.email} onChange={email=>setValues({...values,email})}/><Field label={t('organizations.website')} value={values.website??''} error={errors.website} onChange={website=>setValues({...values,website})}/><button disabled={mutation.isPending}>{t('common.save')}</button>
 </form></section>;
}
function Field({label,value,error,onChange}:{label:string;value:string;error?:string;onChange:(v:string)=>void}){return <label>{label}<input value={value} onChange={e=>onChange(e.target.value)}/>{error&&<small className="field-error">{error}</small>}</label>}
