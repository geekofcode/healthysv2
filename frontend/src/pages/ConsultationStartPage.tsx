import {FormEvent,useState} from 'react';
import {useMutation,useQuery} from '@tanstack/react-query';
import {useNavigate,useSearchParams} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {startConsultation} from '../api/consultations';
import {listPatients} from '../api/patients';
import {getMyProfessional} from '../api/professionalOnboarding';
import {useAuth} from '../auth/AuthContext';
import {keycloak} from '../auth/keycloak';
import {selectedOrganization} from '../auth/organizationContext';
import {ApiErrorMessage} from '../components/ApiErrorMessage';

export function ConsultationStartPage(){
 const {t}=useTranslation();const navigate=useNavigate();const auth=useAuth();const delegated=auth.hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN');const [params]=useSearchParams();
 const context=selectedOrganization(keycloak.tokenParsed?.sub);const organizationId=context==='independent'?undefined:context??keycloak.tokenParsed?.healthys_organization_id as string|undefined??keycloak.tokenParsed?.organization_id as string|undefined;
 const patients=useQuery({queryKey:['patients','clinical-selection',organizationId],queryFn:()=>listPatients()});const professional=useQuery({queryKey:['professional-onboarding','my-professional'],queryFn:getMyProfessional,enabled:!delegated});
 const [form,setForm]=useState({patientId:params.get('patientId')??'',professionalId:'',organizationId:'',appointmentId:params.get('appointmentId')??'',type:'CONSULTATION',reason:''});
 const mutation=useMutation({mutationFn:()=>startConsultation({...form,professionalId:delegated?form.professionalId:professional.data?.id??'',organizationId:delegated?form.organizationId||undefined:organizationId,appointmentId:form.appointmentId||undefined}),onSuccess:value=>navigate(`/consultations/${value.id}`)});
 const submit=(e:FormEvent)=>{e.preventDefault();if(!delegated&&!professional.data?.id)return;mutation.mutate()};
 return <section><p className="eyebrow">{t('consultation.eyebrow')}</p><h1>{t('consultation.start')}</h1><form className="entity-form" onSubmit={submit}>
 <label>{t('consultation.patientId')}<select required value={form.patientId} onChange={e=>setForm({...form,patientId:e.target.value})}><option value="">—</option>{patients.data?.content.map(p=><option key={p.id} value={p.id}>{p.patientNumber}</option>)}</select></label>
 {delegated&&<><label>{t('consultation.professionalId')}<input required value={form.professionalId} onChange={e=>setForm({...form,professionalId:e.target.value})}/></label><label>{t('consultation.organizationId')}<input value={form.organizationId} onChange={e=>setForm({...form,organizationId:e.target.value})}/></label></>}
 <label>{t('consultation.appointmentId')}<input value={form.appointmentId} onChange={e=>setForm({...form,appointmentId:e.target.value})}/></label><label>{t('consultation.type')}<input required value={form.type} onChange={e=>setForm({...form,type:e.target.value})}/></label><label>{t('consultation.reason')}<input value={form.reason} onChange={e=>setForm({...form,reason:e.target.value})}/></label><button disabled={mutation.isPending||(!delegated&&!professional.data?.id)||!patients.data?.content.some(p=>p.id===form.patientId)}>{t('consultation.startAction')}</button></form>
 {mutation.isError&&<ApiErrorMessage error={mutation.error}/>} {patients.isError&&<ApiErrorMessage error={patients.error}/>} {professional.isError&&<ApiErrorMessage error={professional.error}/>}</section>;
}
