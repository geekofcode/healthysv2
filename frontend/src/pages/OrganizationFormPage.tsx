import {FormEvent,useEffect,useState} from 'react';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {Link,useNavigate,useParams} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {createOrganization,getOrganization,listOrganizationTypes,organizationKeys,OrganizationInput,updateOrganization} from '../api/organizations';
import {ApiErrorMessage} from '../components/ApiErrorMessage';
export function OrganizationFormPage(){
 const {t,i18n}=useTranslation();const fr=i18n.language.startsWith('fr');const {id}=useParams();const navigate=useNavigate();const client=useQueryClient();
 const query=useQuery({queryKey:id?organizationKeys.detail(id):['organization','new'],queryFn:()=>getOrganization(id!),enabled:Boolean(id)});
 const types=useQuery({queryKey:['organizations','types'],queryFn:listOrganizationTypes});
 const [values,setValues]=useState<OrganizationInput>({number:'',name:'',status:'ACTIVE'});
 useEffect(()=>{if(query.data){const {number,name,legalName,organizationTypeId,phone,email,website,status}=query.data;setValues({number,name,legalName,organizationTypeId,phone,email,website,status});}},[query.data]);
 const mutation=useMutation({mutationFn:(input:OrganizationInput)=>id?updateOrganization(id,input):createOrganization(input),onSuccess:async result=>{await client.invalidateQueries({queryKey:organizationKeys.all});navigate(`/organizations/${result.id}`);}});
 const submit=(event:FormEvent<HTMLFormElement>)=>{event.preventDefault();mutation.mutate({...values,organizationTypeId:values.organizationTypeId||undefined});};
 if(id&&query.isPending)return <p>{t('common.loading')}</p>;
 if(id&&query.isError)return <ApiErrorMessage error={query.error}/>;
 return <section><div className="page-heading"><div><p className="eyebrow">{t('organizations.title')}</p><h1>{t(id?'organizations.edit':'organizations.create')}</h1></div></div>{mutation.isError&&<ApiErrorMessage error={mutation.error}/>}<form className="entity-form" onSubmit={submit}>
 <fieldset><legend>{fr?'Informations de l’établissement':'Organization information'}</legend><div className="form-grid">
 <Field label={t('organizations.number')} value={values.number} disabled={Boolean(id)} required maxLength={50} onChange={number=>setValues({...values,number})}/>
 <Field label={t('organizations.name')} value={values.name} required onChange={name=>setValues({...values,name})}/>
 <Field label={t('organizations.legalName')} value={values.legalName??''} onChange={legalName=>setValues({...values,legalName})}/>
 <label>{fr?'Type d’établissement':'Organization type'}<select value={values.organizationTypeId??''} onChange={e=>setValues({...values,organizationTypeId:e.target.value})}><option value="">{t('common.none')}</option>{types.data?.map(type=><option key={type.id} value={type.id}>{type.label}</option>)}</select>{types.isError&&<ApiErrorMessage error={types.error}/>}</label>
 <label>{t('organizations.status')}<select value={values.status??'ACTIVE'} onChange={e=>setValues({...values,status:e.target.value})}>{['ACTIVE','INACTIVE'].map(s=><option key={s}>{t(`status.${s}`,s)}</option>)}</select></label>
 </div></fieldset><fieldset><legend>{fr?'Coordonnées':'Contact details'}</legend><div className="form-grid">
 <Field label={t('organizations.phone')} type="tel" maxLength={50} value={values.phone??''} onChange={phone=>setValues({...values,phone})}/>
 <Field label={t('organizations.email')} type="email" value={values.email??''} onChange={email=>setValues({...values,email})}/>
 <Field label={t('organizations.website')} type="url" value={values.website??''} onChange={website=>setValues({...values,website})}/>
 </div></fieldset><div className="form-actions"><button disabled={mutation.isPending}>{t('common.save')}</button><Link className="button secondary" to={id?`/organizations/${id}`:'/organizations'}>{t('common.cancel')}</Link></div>
 </form></section>;
}
function Field({label,value,onChange,type='text',required=false,disabled=false,maxLength=255}:{label:string;value:string;onChange:(v:string)=>void;type?:string;required?:boolean;disabled?:boolean;maxLength?:number}){return <label>{label}{required?' *':''}<input type={type} value={value} required={required} disabled={disabled} maxLength={maxLength} onChange={e=>onChange(e.target.value)}/></label>}
