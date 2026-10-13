import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {Link} from 'react-router-dom';
import {apiRequest} from '../api/client';
import {ApiErrorMessage} from '../components/ApiErrorMessage';
export type PatientCareVisit={id:string;episodeId:string;organizationId:string;organizationName:string;professionalId?:string;doctorName?:string;scheduledAt?:string;status:string;reason?:string;followUp:boolean};
export const listMyCareVisits=()=>apiRequest<PatientCareVisit[]>('/care/visits/me');
export function PatientCareAgendaPage(){
 const {i18n}=useTranslation();const fr=i18n.language.startsWith('fr');const visits=useQuery({queryKey:['care','visits','me'],queryFn:listMyCareVisits});
 return <section><h1>{fr?'Mes visites':'My visits'}</h1><p>{fr?'L’établissement organise et déplace vos visites après votre accueil et votre paiement à la caisse.':'Your establishment schedules and reschedules visits after reception and cash payment.'}</p><Link to="/agenda/legacy">{fr?'Consulter l’ancien historique':'View previous history'}</Link>{visits.isPending&&<p>{fr?'Chargement…':'Loading…'}</p>}{visits.isError&&<ApiErrorMessage error={visits.error}/>} {visits.data?.length===0&&<p>{fr?'Aucune visite.':'No visits.'}</p>}{visits.data?.map(v=><article className="structure-card" key={v.id}><h2>{v.organizationName}</h2><p>{v.scheduledAt?new Date(v.scheduledAt).toLocaleString(i18n.language):fr?'Date à programmer':'Scheduling pending'} · {v.doctorName??'—'}</p><p>{v.status} {v.followUp?(fr?'· Suivi':'· Follow-up'):''}</p>{v.reason&&<p>{v.reason}</p>}</article>)}</section>;
}
