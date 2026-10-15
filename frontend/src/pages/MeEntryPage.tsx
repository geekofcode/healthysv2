import {useAuth} from '../auth/AuthContext';
import {MePage} from './MePage';
import {PatientHealthProfilePage} from './PatientHealthProfilePage';
export function MeEntryPage(){const auth=useAuth();return auth.hasAnyRole('PATIENT')?<PatientHealthProfilePage/>:<MePage/>;}
