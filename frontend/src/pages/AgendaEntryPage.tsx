import {useAuth} from '../auth/AuthContext';
import {AgendaPage} from './AgendaPage';
import {CareCalendarPage} from './CareCalendarPage';
export function AgendaEntryPage(){
 const auth=useAuth();
 return auth.hasAnyRole('DOCTOR','NURSE','HOSPITAL_ADMIN','HOSPITAL_AGENT','PLATFORM_ADMIN')?<CareCalendarPage/>:<AgendaPage/>;
}
