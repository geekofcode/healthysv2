import {useAuth} from '../auth/AuthContext';
import {PatientConsultationsPage} from './PatientConsultationsPage';
import {CareCalendarPage} from './CareCalendarPage';
export function ConsultationsEntryPage(){const auth=useAuth();return auth.hasAnyRole('PATIENT')?<PatientConsultationsPage/>:<CareCalendarPage/>;}
