import {useState} from 'react';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {getReceptionOptions,getScheduledCareVisit,scheduleCareVisit,type CareEpisode} from '../api/careEpisodes';
import {ApiErrorMessage} from './ApiErrorMessage';
function localInput(iso?:string){if(!iso)return '';const date=new Date(iso);return new Date(date.getTime()-date.getTimezoneOffset()*60000).toISOString().slice(0,16);}
export function VisitScheduleForm({episodeId,visit,specialityId,fr}:{episodeId:string;visit:CareEpisode['visits'][number];specialityId?:string;fr:boolean}){
 const cache=useQueryClient();const [professionalId,setProfessionalId]=useState(visit.professionalId??'');const [scheduledAt,setScheduledAt]=useState(localInput(visit.scheduledAt));const [reason,setReason]=useState('');
 const detail=useQuery({queryKey:['care-visit',visit.id],queryFn:()=>getScheduledCareVisit(visit.id)});
 const options=useQuery({queryKey:['care-reception-options'],queryFn:getReceptionOptions});
 const schedule=useMutation({mutationFn:()=>scheduleCareVisit(visit.id,{professionalId,scheduledAt:new Date(scheduledAt).toISOString(),version:visit.version,reason:reason.trim()||undefined}),onSuccess:()=>{void cache.invalidateQueries({queryKey:['care-episodes',episodeId]});void cache.invalidateQueries({queryKey:['care-calendar']});void cache.invalidateQueries({queryKey:['care-visit',visit.id]});}});
 return <form className="entity-form" onSubmit={e=>{e.preventDefault();schedule.mutate();}}>
  <h3>{visit.status==='SCHEDULED'?(fr?'Reporter ou changer de médecin':'Reschedule or change doctor'):(fr?'Programmer la visite':'Schedule visit')}</h3>
  <label>{fr?'Médecin':'Doctor'}<select required value={professionalId} onChange={e=>setProfessionalId(e.target.value)}><option value="">—</option>{options.data?.doctors.filter(d=>!specialityId||d.specialityIds.includes(specialityId)).map(d=><option key={d.professionalId} value={d.professionalId}>{d.firstName} {d.lastName}</option>)}</select></label>
  <label>{fr?'Date et heure':'Date and time'}<input required type="datetime-local" min={visit.status==='SCHEDULED'?localInput(visit.scheduledAt):undefined} value={scheduledAt} onChange={e=>setScheduledAt(e.target.value)}/></label>
  <label>{fr?'Motif du changement':'Reason for change'}<textarea maxLength={2000} value={reason} onChange={e=>setReason(e.target.value)}/></label>
  <p>{fr?'Le paiement et l’épisode sont conservés. Le serveur vérifie l’affectation et la disponibilité du médecin.':'Payment and episode are preserved. The server verifies doctor assignment and availability.'}</p>
  <button disabled={schedule.isPending}>{fr?'Enregistrer la programmation':'Save schedule'}</button>
  {detail.data&&detail.data.history.length>0&&<details><summary>{fr?'Historique de programmation':'Scheduling history'}</summary><ul>{detail.data.history.map(entry=><li key={entry.id}>{entry.changedAt&&new Date(entry.changedAt).toLocaleString()} · {entry.status}{entry.scheduledAt&&` · ${new Date(entry.scheduledAt).toLocaleString()}`}{entry.reason&&` · ${entry.reason}`}</li>)}</ul></details>}
  {[options.error,detail.error,schedule.error].filter(Boolean).map((error,index)=><ApiErrorMessage key={index} error={error}/>)}
  {schedule.isError&&<button type="button" onClick={()=>{schedule.reset();void cache.invalidateQueries({queryKey:['care-episodes',episodeId]});}}>{fr?'Recharger la visite':'Reload visit'}</button>}
 </form>;
}
