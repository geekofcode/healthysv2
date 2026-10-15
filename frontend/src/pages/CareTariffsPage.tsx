import {Link} from 'react-router-dom';
import {useHospitalPermission} from '../auth/HospitalPermissions';
import {useState} from 'react';
import {useQuery,useMutation,useQueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {createCareTariff,listCareTariffs,updateCareTariff,type CareTariff,type TariffInput} from '../api/careBilling';
import {BillingOrganizationSelect} from '../components/BillingOrganizationSelect';
import {ApiErrorMessage} from '../components/ApiErrorMessage';
import {useAuth} from '../auth/AuthContext';

export function CareTariffsPage(){
 const {t,i18n}=useTranslation();const fr=i18n.language.startsWith('fr');const auth=useAuth();const cache=useQueryClient();
 const [organizationId,setOrganizationId]=useState('');const [editing,setEditing]=useState<CareTariff>();
 const blank:TariffInput={organizationId,code:'',description:'',currency:'CAD',unitPrice:0.01,taxRate:0,active:true};
 const [form,setForm]=useState<TariffInput>(blank);
 const tariffs=useQuery({queryKey:['care-tariffs',organizationId],queryFn:()=>listCareTariffs(organizationId),enabled:!!organizationId});
 const save=useMutation({mutationFn:()=>editing?updateCareTariff(editing.id,{...form,organizationId,version:editing.version}):createCareTariff({...form,organizationId}),onSuccess:()=>{setEditing(undefined);setForm(blank);void cache.invalidateQueries({queryKey:['care-tariffs',organizationId]});}});
 const tariffCreate=useHospitalPermission('tariffs','CREATE');const tariffUpdate=useHospitalPermission('tariffs','UPDATE');
 const canManage=tariffCreate||tariffUpdate;
 return <section><Link to="/billing/speciality-policies">{t('careFlow.specialityPolicies')}</Link>
  <h1>{fr?'Tarifs de soins':'Care tariffs'}</h1>
  <BillingOrganizationSelect label={fr?'Établissement':'Organization'} value={organizationId} onChange={id=>{setOrganizationId(id);setEditing(undefined);setForm({...blank,organizationId:id});}}/>
  {tariffs.isError&&<ApiErrorMessage error={tariffs.error}/>}
  <div className="table-scroll"><table><thead><tr><th>Code</th><th>{fr?'Prestation':'Service'}</th><th>{fr?'Prix':'Price'}</th><th>{fr?'Taxe (%)':'Tax (%)'}</th><th>{fr?'Actif':'Active'}</th><th/></tr></thead><tbody>
   {tariffs.data?.map(tariff=><tr key={tariff.id}><td>{tariff.code}</td><td>{tariff.description}</td><td>{new Intl.NumberFormat(i18n.language,{style:'currency',currency:tariff.currency}).format(tariff.unitPrice)}</td><td>{tariff.taxRate}</td><td>{tariff.active?'✓':'—'}</td><td>{canManage&&tariffUpdate&&<button onClick={()=>{setEditing(tariff);setForm(tariff);}}>{fr?'Modifier':'Edit'}</button>}</td></tr>)}
  </tbody></table></div>
  {canManage&&(editing?tariffUpdate:tariffCreate)&&organizationId&&<form className="entity-form form-grid" onSubmit={event=>{event.preventDefault();save.mutate();}}>
   <h2>{editing?(fr?'Modifier le tarif':'Edit tariff'):(fr?'Créer un tarif':'Create tariff')}</h2>
   <label>Code<input required maxLength={80} value={form.code} onChange={e=>setForm({...form,code:e.target.value})}/></label>
   <label>{fr?'Prestation':'Service'}<input required maxLength={500} value={form.description} onChange={e=>setForm({...form,description:e.target.value})}/></label>
   <label>{fr?'Devise':'Currency'}<select value={form.currency} onChange={e=>setForm({...form,currency:e.target.value})}>{['CAD','USD','EUR','XAF','XOF'].map(currency=><option key={currency}>{currency}</option>)}</select></label>
   <label>{fr?'Prix unitaire':'Unit price'}<input required type="number" min="0.01" step="0.01" value={form.unitPrice} onChange={e=>setForm({...form,unitPrice:Number(e.target.value)})}/></label>
   <label>{fr?'Taxe (%)':'Tax (%)'}<input required type="number" min="0" max="100" step="0.01" value={form.taxRate} onChange={e=>setForm({...form,taxRate:Number(e.target.value)})}/></label>
   <label><input type="checkbox" checked={form.active} onChange={e=>setForm({...form,active:e.target.checked})}/>{fr?'Actif':'Active'}</label>
   <button disabled={save.isPending}>{fr?'Enregistrer':'Save'}</button>
   {editing&&<button type="button" onClick={()=>{setEditing(undefined);setForm(blank);}}>{fr?'Annuler':'Cancel'}</button>}
   {save.isError&&<ApiErrorMessage error={save.error}/>}
  </form>}
 </section>;
}
