import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {getCarePatientHistory} from '../api/careHistory';
import {ApiErrorMessage} from './ApiErrorMessage';
const labels:Record<'reason'|'observations'|'diagnosis'|'treatment'|'followUp',[string,string]>={reason:['Motif','Reason'],observations:['Observations','Observations'],diagnosis:['Diagnostic','Diagnosis'],treatment:['Traitement','Treatment'],followUp:['Suivi et recommandations','Follow-up and recommendations']};
/** Authorized validated reports only; no dependency on AI subscription or provider. */
export function CarePatientHistory({patientId,currentVisitId}:{patientId:string;currentVisitId?:string}){
 const {i18n}=useTranslation();const fr=i18n.language.startsWith('fr');
 const history=useQuery({queryKey:['care','patient-history',patientId],queryFn:()=>getCarePatientHistory(patientId),enabled:!!patientId});
 const records=history.data?.filter(entry=>entry.visitId!==currentVisitId);
 return <section aria-label={fr?'Historique des consultations validées':'Validated consultation history'}><h2>{fr?'Historique des consultations validées':'Validated consultation history'}</h2><p>{fr?'Comptes rendus validés accessibles selon vos autorisations, en lecture seule.':'Validated reports available under your current access permissions, read only.'}</p>{history.isPending&&<p>{fr?'Chargement de l’historique…':'Loading history…'}</p>}{history.isError&&<ApiErrorMessage error={history.error}/>} {records?.length===0&&<p>{fr?'Aucun compte rendu antérieur accessible.':'No earlier authorized reports.'}</p>}{records?.map(entry=><article className="structure-card" key={`${entry.visitId}-${entry.revision}`}><h3>{entry.organizationName} · {entry.doctorName}</h3><p>{new Date(entry.validatedAt).toLocaleString(i18n.language)} · {fr?'Version':'Revision'} {entry.revision}{entry.historical?(fr?' · Historique importé':' · Imported history'):''}</p><dl>{(Object.keys(labels) as (keyof typeof labels)[]).map(field=><div key={field}><dt>{labels[field][fr?0:1]}</dt><dd style={{whiteSpace:'pre-wrap'}}>{entry[field]||'—'}</dd></div>)}</dl></article>)}</section>;
}
