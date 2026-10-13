import {useState} from 'react';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {listMyDataDeletionRequests,requestMyDataDeletion} from '../api/dataDeletion';
import {ApiErrorMessage} from './ApiErrorMessage';

export function DataDeletionPanel(){
 const {i18n}=useTranslation();const fr=i18n.language.startsWith('fr');const cache=useQueryClient();
 const [confirmation,setConfirmation]=useState('');const [requestKey,setRequestKey]=useState(()=>crypto.randomUUID());
 const requests=useQuery({queryKey:['me','data-deletion'],queryFn:listMyDataDeletionRequests,refetchInterval:q=>q.state.data?.some(r=>['QUEUED','RUNNING'].includes(r.status))?5000:false});
 const mutation=useMutation({mutationFn:()=>requestMyDataDeletion(requestKey),onSuccess:async()=>{setConfirmation('');setRequestKey(crypto.randomUUID());await cache.invalidateQueries({queryKey:['me','data-deletion']})}});
 const active=requests.data?.some(r=>['QUEUED','RUNNING'].includes(r.status));
 const statuses=fr?{QUEUED:'En attente',RUNNING:'En cours',COMPLETED:'Traitée',FAILED:'Échec — prise en charge nécessaire'}:{QUEUED:'Queued',RUNNING:'In progress',COMPLETED:'Processed',FAILED:'Failed — support required'};
 return <section className="entity-form" aria-label={fr?'Suppression de mes données':'Delete my data'}><h2>{fr?'Suppression de mes données':'Delete my data'}</h2>
 <p>{fr?'Demandez la suppression de vos données personnelles et fichiers. Le traitement est asynchrone. Les dossiers médicaux et factures partagés nécessaires à la continuité des soins ou à la traçabilité sont conservés ; votre identité est anonymisée lorsque cela est possible, et les références d’auteur nécessaires à la traçabilité sont conservées. Les résultats ci-dessous précisent ce qui a été supprimé, anonymisé ou conservé.':'Request removal of your personal data and files. Processing is asynchronous. Shared medical records and invoices needed for continuity of care or traceability are retained; your identity is anonymized where possible, while author references needed for traceability are retained. The results below identify removed, anonymized and retained records.'}</p>
 <p>{fr?'Cette action est irréversible pour les données supprimées. Elle ne supprime pas automatiquement votre compte de connexion.':'Removal is irreversible. This request does not automatically delete your sign-in account.'}</p>
 <form onSubmit={e=>{e.preventDefault();if(confirmation==='SUPPRIMER'&&!active&&requests.isSuccess)mutation.mutate()}}><label>{fr?'Tapez SUPPRIMER pour confirmer':'Type SUPPRIMER to confirm'}<input autoComplete="off" value={confirmation} onChange={e=>setConfirmation(e.target.value)} disabled={mutation.isPending||active}/></label><button className="danger" disabled={confirmation!=='SUPPRIMER'||mutation.isPending||active||!requests.isSuccess}>{fr?'Demander la suppression':'Request deletion'}</button></form>
 {requests.isPending&&<p>{fr?'Chargement des demandes…':'Loading requests…'}</p>}{requests.isError&&<ApiErrorMessage error={requests.error}/>} {mutation.isError&&<ApiErrorMessage error={mutation.error}/>} {mutation.isSuccess&&<p role="status">{fr?'Demande enregistrée. Consultez son avancement ci-dessous.':'Request recorded. Follow its progress below.'}</p>}
 <div aria-live="polite">{requests.data?.map(r=><article className="structure-card" key={r.id}><h3>{statuses[r.status]}</h3><p>{new Date(r.createdAt).toLocaleString(i18n.language)}</p><p>{fr?'Supprimés':'Removed'} : {r.removedCount} · {fr?'Anonymisés':'Anonymized'} : {r.anonymizedCount} · {fr?'Conservés':'Retained'} : {r.retainedCount}</p>{r.notes&&<p>{r.notes}</p>}{r.completedAt&&<p>{fr?'Fin du traitement':'Processing ended'} : {new Date(r.completedAt).toLocaleString(i18n.language)}</p>}</article>)}</div></section>;
}
