import {useHospitalPermission} from '../auth/HospitalPermissions';
import {useState} from 'react';
import {Link} from 'react-router-dom';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {useAuth} from '../auth/AuthContext';
import {ApiErrorMessage} from '../components/ApiErrorMessage';
import {getReceptionOptions} from '../api/careEpisodes';
import {callCareNext,getCareCalendar,reorderCareQueue,setCarePresence} from '../api/careCalendar';

export function CareCalendarPage(){
 const {i18n}=useTranslation();const fr=i18n.language.startsWith('fr');const auth=useAuth();const cache=useQueryClient();
 const [date,setDate]=useState(()=>new Date().toLocaleDateString('en-CA'));const [professionalId,setProfessionalId]=useState('');
 const queueUpdate=useHospitalPermission('queue','UPDATE');
 const isDoctor=auth.hasAnyRole('DOCTOR');const canReceive=auth.hasAnyRole('NURSE','HOSPITAL_ADMIN','HOSPITAL_AGENT','PLATFORM_ADMIN');
 const options=useQuery({queryKey:['care-reception-options'],queryFn:getReceptionOptions,enabled:canReceive});
 const key=['care-calendar',date,professionalId];const calendar=useQuery({queryKey:key,queryFn:()=>getCareCalendar(date,professionalId||undefined),enabled:isDoctor||!!professionalId});
 const refresh=()=>cache.invalidateQueries({queryKey:['care-calendar']});
 const presence=useMutation({mutationFn:setCarePresence,onSuccess:refresh});const next=useMutation({mutationFn:callCareNext,onSuccess:refresh});const reorder=useMutation({mutationFn:reorderCareQueue,onSuccess:refresh});
 const busy=presence.isPending||next.isPending||reorder.isPending;
 const error=presence.error||next.error||reorder.error;
 const move=(index:number,direction:number)=>{if(!calendar.data)return;const ids=calendar.data.visits.map(v=>v.visitId);[ids[index],ids[index+direction]]=[ids[index+direction]!,ids[index]!];reorder.mutate({professionalId:calendar.data.professionalId,date,visitIds:ids,expectedVersion:calendar.data.version});};
 return <section>
  <h1>{fr?'Calendrier et file médecin':'Doctor calendar and queue'}</h1>
  <div className="form-grid">
   <label>{fr?'Date':'Date'}<input type="date" required value={date} onChange={e=>setDate(e.target.value)}/></label>
   {canReceive&&<label>{fr?'Médecin':'Doctor'}<select value={professionalId} onChange={e=>setProfessionalId(e.target.value)}><option value="">—</option>{options.data?.doctors.map(d=><option key={d.professionalId} value={d.professionalId}>{d.firstName} {d.lastName}</option>)}</select></label>}
  </div>
  <p>{fr?'Les visites sont affichées pour la date dans le fuseau horaire de l’établissement. L’ordre initial suit l’enregistrement.':'Visits use the organization’s timezone. Initial order follows registration.'}</p>
  {isDoctor&&queueUpdate&&calendar.data&&<button disabled={busy||!calendar.data.visits.some(v=>v.presence==='PRESENT')} onClick={()=>next.mutate({professionalId:calendar.data!.professionalId,date,expectedVersion:calendar.data!.version})}>{fr?'Appeler le prochain patient':'Call next patient'}</button>}
  {calendar.isFetching&&<p>…</p>}
  {[options.error,calendar.error,error].filter(Boolean).map((value,index)=><ApiErrorMessage key={index} error={value}/>)}
  {error&&<button onClick={()=>{presence.reset();next.reset();reorder.reset();void refresh();}}>{fr?'Actualiser la file avant de réessayer':'Reload queue before retrying'}</button>}
  <div className="table-scroll"><table><thead><tr><th>#</th><th>{fr?'Heure':'Time'}</th><th>Patient</th><th>{fr?'Statut':'Status'}</th><th>{fr?'Présence':'Presence'}</th><th/></tr></thead><tbody>
   {calendar.data?.visits.map((visit,index)=><tr key={visit.visitId}>
    <td>{index+1}</td><td>{new Date(visit.scheduledAt).toLocaleTimeString(i18n.language,{hour:'2-digit',minute:'2-digit',timeZone:calendar.data!.timezone})}</td>
    <td><Link to={`/care/episodes/${visit.episodeId}`}>{visit.patientFirstName} {visit.patientLastName}</Link></td><td>{visit.status}</td><td>{visit.presence}</td>
    <td><div className="card-actions">
     {isDoctor&&<Link to={`/care/visits/${visit.visitId}`}>{fr?'Ouvrir la consultation':'Open consultation'}</Link>}
     {canReceive&&queueUpdate&&visit.presence!=='CALLED'&&<button disabled={busy} onClick={()=>presence.mutate({visitId:visit.visitId,present:visit.presence!=='PRESENT',expectedVersion:calendar.data!.version})}>{visit.presence==='PRESENT'?(fr?'Marquer absent':'Mark absent'):(fr?'Marquer présent':'Mark present')}</button>}
     {isDoctor&&queueUpdate&&<><button aria-label={fr?'Monter':'Move up'} disabled={busy||index===0} onClick={()=>move(index,-1)}>↑</button><button aria-label={fr?'Descendre':'Move down'} disabled={busy||index===calendar.data!.visits.length-1} onClick={()=>move(index,1)}>↓</button></>}
    </div></td>
   </tr>)}
  </tbody></table></div>
  {calendar.data&&!calendar.data.visits.length&&<p>{fr?'Aucune visite pour cette date.':'No visits on this date.'}</p>}
 </section>;
}
