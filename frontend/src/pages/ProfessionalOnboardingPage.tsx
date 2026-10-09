import {useState,type FormEvent} from 'react';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {getMyDossier,listCountries,listSpecialities,onboardingKeys,saveDossier,submitDossier,uploadProof,type DossierInput} from '../api/professionalOnboarding';
import {ApiErrorMessage} from '../components/ApiErrorMessage';
import {useAuth} from '../auth/AuthContext';
export function ProfessionalOnboardingPage(){
 const {t}=useTranslation();const auth=useAuth();const client=useQueryClient();const query=useQuery({queryKey:onboardingKeys.me,queryFn:getMyDossier,refetchInterval:q=>q.state.data?.status==='APPROVED'&&q.state.data.roleSyncStatus!=='SYNCED'?10000:false});
 const countries=useQuery({queryKey:['onboarding-countries'],queryFn:listCountries});const specialities=useQuery({queryKey:['onboarding-specialities'],queryFn:listSpecialities});
 const refresh=()=>client.invalidateQueries({queryKey:onboardingKeys.me});const save=useMutation({mutationFn:saveDossier,onSuccess:refresh});const upload=useMutation({mutationFn:uploadProof,onSuccess:refresh});const submit=useMutation({mutationFn:submitDossier,onSuccess:refresh});
 const [fileError,setFileError]=useState(false);const dossier=query.data;const editable=!dossier||['DRAFT','REJECTED'].includes(dossier.status);const busy=save.isPending||upload.isPending||submit.isPending;
 function handleSave(e:FormEvent<HTMLFormElement>){e.preventDefault();const data=new FormData(e.currentTarget);save.mutate({profession:data.get('profession') as DossierInput['profession'],licenseNumber:String(data.get('licenseNumber')).trim(),issuingAuthority:String(data.get('issuingAuthority')).trim(),countryId:String(data.get('countryId')),specialityCatalogId:String(data.get('specialityCatalogId'))||undefined});}
 if(query.isPending)return <p>{t('common.loading')}</p>;if(query.isError)return <ApiErrorMessage error={query.error}/>;
 return <section className="professional-onboarding"><h1>{t('onboarding.title')}</h1><p>{t('onboarding.description')}</p><p>{t('onboarding.independent')}</p>
 {dossier&&<article className="structure-card" aria-live="polite"><strong>{t(`onboarding.status.${dossier.status}`)}</strong>{dossier.reason&&<p>{dossier.reason}</p>}{dossier.status==='APPROVED'&&dossier.roleSyncStatus&&dossier.roleSyncStatus!=='SYNCED'&&<p>{t(dossier.roleSyncStatus==='FAILED'?'onboarding.roleSyncFailed':'onboarding.roleSyncPending')}</p>}{dossier.status==='APPROVED'&&dossier.roleSyncStatus==='SYNCED'&&<button type="button" onClick={()=>void auth.login(`${window.location.origin}/`)}>{t('onboarding.refreshAccess')}</button>}</article>}
 {editable&&<form key={dossier?.updatedAt??'new'} className="entity-form" onSubmit={handleSave}>
 <label>{t('onboarding.profession')}<select name="profession" defaultValue={dossier?.profession??'medecin'} required>{(['medecin','nurse','laboratoire'] as const).map(p=><option key={p} value={p}>{t(`onboarding.professions.${p}`)}</option>)}</select></label>
 <label>{t('onboarding.license')}<input name="licenseNumber" maxLength={100} defaultValue={dossier?.licenseNumber} required/></label>
 <label>{t('onboarding.authority')}<input name="issuingAuthority" maxLength={255} defaultValue={dossier?.issuingAuthority} required/></label>
 <label>{t('onboarding.country')}<select name="countryId" defaultValue={dossier?.countryId??''} required disabled={countries.isPending}><option value="">{t('onboarding.select')}</option>{countries.data?.map(c=><option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
 <label>{t('onboarding.speciality')}<select name="specialityCatalogId" defaultValue={dossier?.specialityCatalogId??''}><option value="">{t('common.none')}</option>{specialities.data?.map(s=><option key={s.id} value={s.id}>{s.name}</option>)}</select></label>
 {countries.data?.length===0&&<p role="alert">{t('onboarding.noCountries')}</p>}<button disabled={busy||!countries.data?.length}>{t('common.save')}</button></form>}
 {editable&&dossier&&<article className="structure-card"><h2>{t('onboarding.proof')}</h2><p>{t('onboarding.proofHint')}</p><label>{t('onboarding.proof')}<input type="file" accept="application/pdf,image/jpeg,image/png" disabled={busy} onChange={e=>{const file=e.target.files?.[0];if(!file)return;const invalid=file.size>5*1024*1024||!['application/pdf','image/jpeg','image/png'].includes(file.type);setFileError(invalid);if(!invalid)upload.mutate(file);}}/></label>{fileError&&<p role="alert">{t('onboarding.proofHint')}</p>}{dossier.proofUploaded&&<p>{t('onboarding.proofReady')}</p>}<button disabled={busy||!dossier.proofUploaded} onClick={()=>submit.mutate()}>{t('onboarding.submit')}</button></article>}
 {[save,upload,submit].map((m,i)=>m.isError&&<ApiErrorMessage key={i} error={m.error}/>)}{countries.isError&&<ApiErrorMessage error={countries.error}/>} {specialities.isError&&<ApiErrorMessage error={specialities.error}/>}</section>;
}
