import {useState,type FormEvent} from 'react';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {getMyDossier,listCountries,listSpecialities,onboardingKeys,saveDossier,submitDossier,uploadDocument,type DossierInput,type DocumentKind} from '../api/professionalOnboarding';
import {ApiErrorMessage} from '../components/ApiErrorMessage';
import {useAuth} from '../auth/AuthContext';
export function ProfessionalOnboardingPage(){
 const {t}=useTranslation();const auth=useAuth();const client=useQueryClient();const query=useQuery({queryKey:onboardingKeys.me,queryFn:getMyDossier,refetchInterval:q=>q.state.data?.status==='APPROVED'&&q.state.data.roleSyncStatus!=='SYNCED'?10000:false});
 const countries=useQuery({queryKey:['onboarding-countries'],queryFn:listCountries});const specialities=useQuery({queryKey:['onboarding-specialities'],queryFn:listSpecialities});
 const refresh=()=>client.invalidateQueries({queryKey:onboardingKeys.me});const save=useMutation({mutationFn:saveDossier,onSuccess:refresh});const upload=useMutation({mutationFn:uploadDocument,onSuccess:refresh});const submit=useMutation({mutationFn:submitDossier,onSuccess:refresh});
 const [fileError,setFileError]=useState(false);const dossier=query.data;const editable=!dossier||['DRAFT','REJECTED'].includes(dossier.status);const busy=save.isPending||upload.isPending||submit.isPending;
 function handleSave(e:FormEvent<HTMLFormElement>){e.preventDefault();const data=new FormData(e.currentTarget);save.mutate({profession:data.get('profession') as DossierInput['profession'],licenseNumber:String(data.get('licenseNumber')).trim(),issuingAuthority:String(data.get('issuingAuthority')).trim(),countryId:String(data.get('countryId')),specialityName:String(data.get('specialityName')).trim()||undefined,identityDocumentType:String(data.get('identityDocumentType')) as DossierInput['identityDocumentType'],identityDocumentNumber:String(data.get('identityDocumentNumber')).trim(),identityExpiresOn:String(data.get('identityExpiresOn'))||undefined});}
 if(query.isPending)return <p>{t('common.loading')}</p>;if(query.isError)return <ApiErrorMessage error={query.error}/>;
 return <section className="professional-onboarding"><h1>{t('onboarding.title')}</h1><p>{t('onboarding.description')}</p><p>{t('onboarding.independent')}</p>
 {dossier&&<article className="structure-card" aria-live="polite"><strong>{t(`onboarding.status.${dossier.status}`)}</strong>{dossier.reason&&<p>{dossier.reason}</p>}{dossier.status==='APPROVED'&&dossier.roleSyncStatus&&dossier.roleSyncStatus!=='SYNCED'&&<p>{t(dossier.roleSyncStatus==='FAILED'?'onboarding.roleSyncFailed':'onboarding.roleSyncPending')}</p>}{dossier.status==='APPROVED'&&dossier.roleSyncStatus==='SYNCED'&&<button type="button" onClick={()=>void auth.login(`${window.location.origin}/`)}>{t('onboarding.refreshAccess')}</button>}</article>}
 {editable&&<form key={dossier?.updatedAt??'new'} className="entity-form" onSubmit={handleSave}>
 <label>{t('onboarding.profession')}<select name="profession" defaultValue={dossier?.profession??'medecin'} required>{(['medecin','nurse','laboratoire'] as const).map(p=><option key={p} value={p}>{t(`onboarding.professions.${p}`)}</option>)}</select></label>
 <label>{t('onboarding.license')}<input name="licenseNumber" maxLength={100} defaultValue={dossier?.licenseNumber} required/></label>
 <label>{t('onboarding.authority')}<input name="issuingAuthority" maxLength={255} defaultValue={dossier?.issuingAuthority} required/></label>
 <label>{t('onboarding.country')}<select name="countryId" defaultValue={dossier?.countryId??''} required disabled={countries.isPending}><option value="">{t('onboarding.select')}</option>{countries.data?.map(c=><option key={c.id} value={c.id}>{c.name}</option>)}</select></label>
 <label>{t('onboarding.speciality')}<input name="specialityName" list="professional-specialities" maxLength={255} defaultValue={dossier?.specialityName??specialities.data?.find(s=>s.id===dossier?.specialityCatalogId)?.name??''}/><datalist id="professional-specialities">{specialities.data?.map(s=><option key={s.id} value={s.name}/>)}</datalist><small>{t('onboarding.specialityHint')}</small></label>
 <label>{t('onboarding.identityType')}<select name="identityDocumentType" defaultValue={dossier?.identityDocumentType??'NATIONAL_ID'} required>{(['PASSPORT','NATIONAL_ID','DRIVING_LICENSE'] as const).map(type=><option key={type} value={type}>{t(`onboarding.identityTypes.${type}`)}</option>)}</select></label>
 <label>{t('onboarding.identityDocumentNumber')}<input name="identityDocumentNumber" maxLength={100} defaultValue={dossier?.identityDocumentNumber} required/></label>
 <label>{t('onboarding.identityExpiresOn')}<input name="identityExpiresOn" type="date" min={minimumIdentityExpiry()} defaultValue={dossier?.identityExpiresOn} required/><small>{t('onboarding.identityExpiryHint')}</small></label>
 {countries.data?.length===0&&<p role="alert">{t('onboarding.noCountries')}</p>}<button disabled={busy||!countries.data?.length}>{t('common.save')}</button></form>}
 {editable&&<article className="structure-card"><h2>{t('onboarding.documents')}</h2>{!dossier&&<p role="status">{t('onboarding.saveBeforeUpload')}</p>}<p>{t('onboarding.proofHint')}</p><div className="form-grid">{(['ID_FRONT','ID_BACK','LICENSE'] as DocumentKind[]).map(kind=><label key={kind}>{t(`onboarding.documentKinds.${kind}`)}<input type="file" aria-label={t(`onboarding.documentKinds.${kind}`)} accept="application/pdf,image/jpeg,image/png" disabled={busy||!dossier} onChange={e=>{const file=e.target.files?.[0];if(!file||!dossier)return;const invalid=file.size>5*1024*1024||!['application/pdf','image/jpeg','image/png'].includes(file.type);setFileError(invalid);if(!invalid)upload.mutate({kind,file});}}/>{(kind==='ID_FRONT'?dossier?.identityFrontUploaded:kind==='ID_BACK'?dossier?.identityBackUploaded:dossier?.proofUploaded)?<small>{t('onboarding.proofReady')}</small>:<small>{t('onboarding.proofMissing')}</small>}</label>)}</div>{fileError&&<p role="alert">{t('onboarding.proofHint')}</p>}<p>{t('onboarding.documentsRequired')}</p><button disabled={busy||!dossier?.identityDocumentNumber?.trim()||!dossier?.proofUploaded||!dossier?.identityFrontUploaded||!dossier?.identityBackUploaded||!dossier?.identityDocumentType||!dossier?.identityExpiresOn||dossier?.identityExpiresOn<minimumIdentityExpiry()} onClick={()=>submit.mutate()}>{t('onboarding.submit')}</button></article>}
 {[save,upload,submit].map((m,i)=>m.isError&&<ApiErrorMessage key={i} error={m.error}/>)}{countries.isError&&<ApiErrorMessage error={countries.error}/>} {specialities.isError&&<ApiErrorMessage error={specialities.error}/>}</section>;
}

// Match Java LocalDate.plusMonths: clamp the day to the destination month.
export function minimumIdentityExpiry(now=new Date()){
 const year=now.getFullYear();const month=now.getMonth()+3;
 const lastDay=new Date(year,month+1,0).getDate();
 const threshold=new Date(year,month,Math.min(now.getDate(),lastDay));
 threshold.setDate(threshold.getDate()+1);
 return `${threshold.getFullYear()}-${String(threshold.getMonth()+1).padStart(2,'0')}-${String(threshold.getDate()).padStart(2,'0')}`;
}
