import {useEffect} from 'react';
import {useMutation} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {provisionPatient} from '../api/persons';
import {PatientAccessPage} from './PatientAccessPage';
import {ApiErrorMessage} from '../components/ApiErrorMessage';
import {useAuth} from '../auth/AuthContext';
export function MyPatientAccessPage(){const {t}=useTranslation();const auth=useAuth();const provision=useMutation({mutationFn:provisionPatient});useEffect(()=>{if(auth.roles.includes('PATIENT')&&provision.isIdle)provision.mutate();},[auth.roles,provision.isIdle,provision.mutate]);if(!auth.roles.includes('PATIENT'))return <p>{t('errors.forbidden')}</p>;if(provision.isError)return <ApiErrorMessage error={provision.error}/>;return provision.data?<PatientAccessPage patientId={provision.data.patientId} patientSelf/>:<p>{t('common.loading')}</p>;}
