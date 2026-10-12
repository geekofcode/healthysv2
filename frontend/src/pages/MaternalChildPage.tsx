import {FormEvent,useState} from 'react';
import {useMutation,useQuery} from '@tanstack/react-query';
import {Link,useNavigate} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {listMyPregnancies,listMyChildren,listPregnancies,maternalKeys,startPregnancy} from '../api/maternalChild';
import {ApiErrorMessage} from '../components/ApiErrorMessage';
import {ClinicalReferenceSelect} from '../components/ClinicalReferenceSelect';
import {ResourceTable} from '../components/ResourceTable';
import {useAuth} from '../auth/AuthContext';

export function MaternalChildPage(){
 const auth=useAuth();
 return auth.hasAnyRole('PATIENT')&&!auth.hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','DOCTOR','NURSE')?<PatientMaternalChildPage/>:<ClinicalMaternalChildPage/>;
}
function PatientMaternalChildPage(){
 const {t,i18n}=useTranslation();const [pregnancyPage,setPregnancyPage]=useState(0);const [childPage,setChildPage]=useState(0);
 const pregnancies=useQuery({queryKey:['maternal-child','mine','pregnancies',pregnancyPage],queryFn:()=>listMyPregnancies(pregnancyPage)});
 const children=useQuery({queryKey:['maternal-child','mine','children',childPage],queryFn:()=>listMyChildren(childPage)});
 return <section><div className="page-heading"><div><p className="eyebrow">{t('maternal.eyebrow')}</p><h1>{t('maternal.title')}</h1></div></div><h2>{t('maternal.pregnancy')}</h2>{pregnancies.isPending&&<p>{t('common.loading')}</p>}{pregnancies.isError&&<ApiErrorMessage error={pregnancies.error}/>} {pregnancies.data&&<ResourceTable data={pregnancies.data} page={pregnancyPage} onPageChange={setPregnancyPage} empty={t('common.empty')} columns={[{label:t('maternal.pregnancy'),render:p=>p.pregnancyNumber},{label:t('maternal.expectedDeliveryDate'),render:p=>new Date(`${p.expectedDeliveryDate}T00:00:00`).toLocaleDateString(i18n.language)},{label:t('common.status'),render:p=>t(`status.${p.status}`,p.status)}]} actions={p=><Link to={`/maternal-child/pregnancies/${p.id}`}>{t('common.view')}</Link>}/>}
 <h2>{t('maternal.childRecord')}</h2>{children.isPending&&<p>{t('common.loading')}</p>}{children.isError&&<ApiErrorMessage error={children.error}/>} {children.data&&<ResourceTable data={children.data} page={childPage} onPageChange={setChildPage} empty={t('common.empty')} columns={[{label:t('maternal.child'),render:c=>`${c.firstName} ${c.lastName}`},{label:t('common.status'),render:c=>t(`status.${c.status}`,c.status)}]} actions={c=><Link to={`/maternal-child/children/${c.childPatientId}`}>{t('common.view')}</Link>}/>}</section>;
}
function ClinicalMaternalChildPage(){
 const {t,i18n}=useTranslation();const fr=i18n.language.startsWith('fr');const auth=useAuth();const navigate=useNavigate();
 const [mother,setMother]=useState('');const [searched,setSearched]=useState('');const [child,setChild]=useState('');const [show,setShow]=useState(false);const [status,setStatus]=useState('');
 const [form,setForm]=useState({motherPatientId:'',estimatedConceptionDate:'',lastMenstrualPeriod:'',expectedDeliveryDate:''});
 const query=useQuery({queryKey:maternalKeys.pregnancies(searched),queryFn:()=>listPregnancies(searched),enabled:Boolean(searched)});
 const create=useMutation({mutationFn:()=>startPregnancy({...form,estimatedConceptionDate:form.estimatedConceptionDate||undefined,lastMenstrualPeriod:form.lastMenstrualPeriod||undefined}),onSuccess:p=>navigate(`/maternal-child/pregnancies/${p.id}`)});
 const clinical=auth.hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','DOCTOR','NURSE');
 const rows=(query.data??[]).filter(p=>!status||p.status===status);
 return <section>
  <div className="page-heading"><div><p className="eyebrow">{t('maternal.eyebrow')}</p><h1>{t('maternal.title')}</h1></div>{clinical&&<button type="button" onClick={()=>setShow(!show)}>{show?t('common.cancel'):t('maternal.startPregnancy')}</button>}</div>
  <form className="search-form" onSubmit={e=>{e.preventDefault();setSearched(mother)}}>
   <ClinicalReferenceSelect required kind="patient" label={t('maternal.motherPatientId')} value={mother} onChange={setMother}/>
   <label>{t('common.status')}<select value={status} onChange={e=>setStatus(e.target.value)}><option value="">{fr?'Tous les statuts':'All statuses'}</option>{[...new Set(query.data?.map(p=>p.status)??[])].map(value=><option key={value} value={value}>{t(`status.${value}`,value)}</option>)}</select></label><button>{t('common.search')}</button>
  </form>
  {query.isError&&<ApiErrorMessage error={query.error}/>}{query.isFetching&&<p>{t('common.loading')}</p>}
  <div className="resource-table"><div className="table-scroll"><table><thead><tr><th>{fr?'Grossesse':'Pregnancy'}</th><th>{t('maternal.expectedDeliveryDate')}</th><th>{t('common.status')}</th><th>{t('common.actions')}</th></tr></thead><tbody>{rows.map(p=><tr key={p.id}><td><Link to={`/maternal-child/pregnancies/${p.id}`}>{p.pregnancyNumber}</Link></td><td>{new Date(`${p.expectedDeliveryDate}T00:00:00`).toLocaleDateString(i18n.language)}</td><td><span className="status-badge">{t(`status.${p.status}`,p.status)}</span></td><td><Link to={`/maternal-child/pregnancies/${p.id}`}>{t('common.view')}</Link></td></tr>)}{!rows.length&&<tr><td colSpan={4} className="empty-state">{searched?t('common.empty'):fr?'Sélectionnez une patiente pour afficher ses grossesses.':'Select a patient to display her pregnancies.'}</td></tr>}</tbody></table></div></div>
  <div className="detail-section"><h2>{t('maternal.childRecord')}</h2><form className="search-form" onSubmit={e=>{e.preventDefault();navigate(`/maternal-child/children/${child}`)}}><ClinicalReferenceSelect required kind="patient" label={t('maternal.childPatientId')} value={child} onChange={setChild}/><button>{t('maternal.openRecord')}</button></form></div>
  {show&&clinical&&<div className="detail-section"><h2>{t('maternal.startPregnancy')}</h2><form className="entity-form form-grid" onSubmit={(e:FormEvent)=>{e.preventDefault();create.mutate()}}><ClinicalReferenceSelect required kind="patient" label={t('maternal.motherPatientId')} value={form.motherPatientId} onChange={motherPatientId=>setForm({...form,motherPatientId})}/><Field name="estimatedConceptionDate" form={form} setForm={setForm} t={t}/><Field name="lastMenstrualPeriod" form={form} setForm={setForm} t={t}/><Field name="expectedDeliveryDate" form={form} setForm={setForm} t={t} required/><div className="form-actions"><button disabled={create.isPending}>{t('maternal.startAction')}</button><button type="button" className="secondary" onClick={()=>setShow(false)}>{t('common.cancel')}</button></div></form>{create.isError&&<ApiErrorMessage error={create.error}/>}</div>}
 </section>;
}
function Field<T extends Record<string,string>>({name,form,setForm,t,required=false}:{name:keyof T&string;form:T;setForm:(v:T)=>void;t:(key:string)=>string;required?:boolean}){return <label>{t(`maternal.${name}`)}<input type="date" required={required} value={form[name]} onChange={e=>setForm({...form,[name]:e.target.value})}/></label>}
