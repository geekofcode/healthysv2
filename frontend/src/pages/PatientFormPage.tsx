import {FormEvent,useState} from 'react';
import {useMutation} from '@tanstack/react-query';
import {useNavigate} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {createPatient} from '../api/patients';
import {ApiErrorMessage} from '../components/ApiErrorMessage';

export function PatientFormPage(){const {t}=useTranslation();const navigate=useNavigate();const [form,setForm]=useState({personId:'',bloodGroup:'',rhesus:'',maritalStatus:'',occupation:'',status:'ACTIVE'});const mutation=useMutation({mutationFn:createPatient,onSuccess:p=>navigate(`/patients/${p.id}`)});const submit=(e:FormEvent)=>{e.preventDefault();mutation.mutate(form)};return <section><p className="eyebrow">{t('patients.eyebrow')}</p><h1>{t('patients.create')}</h1>{mutation.isError&&<ApiErrorMessage error={mutation.error}/>}<form className="entity-form" onSubmit={submit}>{(['personId','bloodGroup','rhesus','maritalStatus','occupation'] as const).map(name=><label key={name}>{t(`patients.${name}`)}<input required={name==='personId'} value={form[name]} onChange={e=>setForm({...form,[name]:e.target.value})}/></label>)}<label>{t('patients.status')}<select value={form.status} onChange={e=>setForm({...form,status:e.target.value})}><option>ACTIVE</option><option>INACTIVE</option></select></label><button disabled={mutation.isPending}>{t('common.create')}</button></form></section>}
