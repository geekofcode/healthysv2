import {useEffect,useState} from 'react';
import {useQuery,useQueryClient} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {getPreferences,updatePreferences} from '../api/preferences';
export function systemLanguage(){return (navigator.languages??[navigator.language]).find(language=>/^(fr|en)(-|$)/i.test(language))?.toLowerCase().startsWith('fr')?'fr':'en';}
export function LanguageSwitch({authenticated=false,hidden=false}:{authenticated?:boolean;hidden?:boolean}){
 const {i18n}=useTranslation();const fr=i18n.language.startsWith('fr');const cache=useQueryClient();
 const preferences=useQuery({queryKey:['person','preferences'],queryFn:getPreferences,enabled:authenticated});
 const [choice,setChoice]=useState<'SYSTEM'|'FR'|'EN'>('SYSTEM');const [failed,setFailed]=useState(false);
 useEffect(()=>{setChoice(authenticated?preferences.data?.language??'SYSTEM':'SYSTEM');},[authenticated,preferences.data?.language]);
 useEffect(()=>{const apply=()=>void i18n.changeLanguage(choice==='SYSTEM'?systemLanguage():choice.toLowerCase());apply();window.addEventListener('languagechange',apply);return()=>window.removeEventListener('languagechange',apply);},[choice,i18n]);
 async function change(language:'SYSTEM'|'FR'|'EN'){
  setChoice(language);setFailed(false);if(!authenticated||!preferences.data)return;
  try{const saved=await updatePreferences({language});cache.setQueryData(['person','preferences'],saved);}catch{setFailed(true);}
 }
 if(hidden)return null;
 return <label>{fr?'Langue de l’interface':'Interface language'}<select aria-label={fr?'Langue de l’interface':'Interface language'} disabled={authenticated&&!preferences.data} value={choice} onChange={event=>void change(event.target.value as 'SYSTEM'|'FR'|'EN')}><option value="SYSTEM">{fr?'Langue du système':'System language'}</option><option value="FR">Français</option><option value="EN">English</option></select>{failed&&<span role="status">{fr?'Préférence non enregistrée':'Preference not saved'}</span>}</label>;
}
