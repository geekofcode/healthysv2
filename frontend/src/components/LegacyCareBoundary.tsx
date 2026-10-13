import {type ReactNode} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {getCareCutover,type CareCutover} from '../api/cutover';
import {ApiErrorMessage} from './ApiErrorMessage';
export function LegacyCareBoundary({writeOnly=false,children}:{writeOnly?:boolean;children:(readOnly:boolean,mode:CareCutover['mode'])=>ReactNode}){
 const {i18n}=useTranslation();const fr=i18n.language.startsWith('fr');const state=useQuery({queryKey:['care','cutover'],queryFn:getCareCutover,staleTime:0,refetchInterval:30000});
 if(state.isPending)return <p>{fr?'Vérification du parcours actif…':'Checking the active care workflow…'}</p>;
 if(state.isError)return <ApiErrorMessage error={state.error}/>;
 const readOnly=state.data.mode==='CARE_ONLY';
 if(readOnly&&writeOnly)return <section><h1>{fr?'Nouveau parcours de soins':'New care workflow'}</h1><p>{fr?'Les consultations démarrent maintenant depuis une visite programmée. L’ancien parcours est disponible uniquement en lecture historique.':'Consultations now start from a scheduled visit. The previous workflow is available as read-only history.'}</p><Link to="/agenda">{fr?'Ouvrir le calendrier':'Open calendar'}</Link></section>;
 return <>{readOnly&&<p role="status">{fr?'Historique en lecture seule — utilisez le calendrier pour la prise en charge actuelle.':'Read-only history — use the calendar for current care.'}</p>}{children(readOnly,state.data.mode)}</>;
}
