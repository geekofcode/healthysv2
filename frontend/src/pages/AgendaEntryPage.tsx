import {LegacyCareBoundary} from '../components/LegacyCareBoundary';
import {PatientCareAgendaPage} from './PatientCareAgendaPage';
import {useAuth} from '../auth/AuthContext';
import {AgendaPage} from './AgendaPage';
import {CareCalendarPage} from './CareCalendarPage';
export function AgendaEntryPage(){
 const auth=useAuth();
 return auth.hasAnyRole('DOCTOR','NURSE','HOSPITAL_ADMIN','HOSPITAL_AGENT','PLATFORM_ADMIN')?<CareCalendarPage/>:<LegacyCareBoundary>{(_readOnly,mode)=>mode!=='LEGACY'?<PatientCareAgendaPage/>:<AgendaPage/>}</LegacyCareBoundary>;
}
