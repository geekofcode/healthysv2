import {LegacyCareBoundary} from '../components/LegacyCareBoundary';
import {PatientCareAgendaPage} from './PatientCareAgendaPage';
import {useAuth} from '../auth/AuthContext';
import {AgendaPage} from './AgendaPage';
import {CareCalendarPage} from './CareCalendarPage';
export function AgendaEntryPage(){
 const auth=useAuth();
 return auth.hasAnyRole('PATIENT')?<PatientCareAgendaPage/>:<CareCalendarPage/>;
}
