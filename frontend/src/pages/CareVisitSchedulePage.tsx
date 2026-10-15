import {useQuery} from '@tanstack/react-query';
import {Link,useParams} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {getCareEpisode,getScheduledCareVisit} from '../api/careEpisodes';
import {VisitScheduleForm} from '../components/VisitScheduleForm';
import {ApiErrorMessage} from '../components/ApiErrorMessage';
export function CareVisitSchedulePage(){const {id=''}=useParams();const {i18n,t}=useTranslation();const visit=useQuery({queryKey:['care-visit',id],queryFn:()=>getScheduledCareVisit(id)});const episode=useQuery({queryKey:['care-episodes',visit.data?.episodeId],queryFn:()=>getCareEpisode(visit.data!.episodeId),enabled:!!visit.data});return <section><h1>{t('careFlow.reassignVisit')}</h1>{visit.data&&episode.data&&(episode.data.status==='DRAFT'||visit.data.status==='PENDING_PAYMENT')?<p role="status">{t('careFlow.payBeforeReschedule')} <Link to={`/care/episodes/${episode.data.id}`}>{t('careFlow.receiveAndPay')}</Link></p>:visit.data&&episode.data&&<VisitScheduleForm episodeId={visit.data.episodeId} visit={visit.data} specialityId={episode.data.specialityId} fr={i18n.language.startsWith('fr')}/>} {[visit,episode].filter(q=>q.isError).map((q,index)=><ApiErrorMessage key={index} error={q.error}/>)}</section>;}
