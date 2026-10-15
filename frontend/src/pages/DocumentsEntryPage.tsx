import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {useAuth} from '../auth/AuthContext';
import {listDocuments,downloadDocument} from '../api/documents';
import {ApiErrorMessage} from '../components/ApiErrorMessage';
import {DocumentsPage} from './DocumentsPage';
export function DocumentsEntryPage(){const auth=useAuth();return auth.hasAnyRole('PATIENT')?<PatientDocuments/>:<DocumentsPage/>;}
function PatientDocuments(){const auth=useAuth();const {t}=useTranslation();const patientId=auth.activeProfile?.sourceId??auth.activeProfile?.id??'';const query=useQuery({queryKey:['patient-documents',auth.activeProfile?.id,patientId],queryFn:()=>listDocuments(patientId,0),enabled:!!patientId});return <section><h1>{t('careFlow.myDocuments')}</h1><p>{auth.activeProfile?.label}</p>{query.isPending&&<p>{t('common.loading')}</p>}{query.error&&<ApiErrorMessage error={query.error}/>} {query.data?.content.map(document=><article className="structure-card" key={document.id}><h2>{document.fileName}</h2><button onClick={()=>void downloadDocument(document)}>{t('documents.download')}</button></article>)}</section>;}
