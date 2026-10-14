import {useContext,useEffect,useState,type ReactNode} from 'react';
import {useQuery} from '@tanstack/react-query';
import {useLocation} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {useAuth} from './AuthContext';
import {keycloak} from './keycloak';
import {selectedOrganization} from './organizationContext';
import {getHospitalAccess} from '../api/hospitalAccess';
import {HospitalPermissionContext as Context,pathPermission,allowsPermission} from './HospitalPermissions';
export function HospitalPermissionProvider({children}:{children:ReactNode}){
 const auth=useAuth();const subject=keycloak.tokenParsed?.sub;const [org,setOrg]=useState(()=>selectedOrganization(subject));
 useEffect(()=>{const update=()=>setOrg(selectedOrganization(subject));window.addEventListener('healthys:organization-changed',update);update();return()=>window.removeEventListener('healthys:organization-changed',update);},[subject]);
 const staff=auth.roles.some(r=>['HOSPITAL_ADMIN','HOSPITAL_AGENT','HOSPITAL_VIEWER','NURSE','DOCTOR','CASHIER','ACCOUNTANT'].includes(r));
 const platform=auth.roles.includes('PLATFORM_ADMIN');
 const access=useQuery({queryKey:['hospital-access',org,'me',subject,auth.activeProfile?.id,auth.activeProfile?.type],queryFn:()=>getHospitalAccess(org!),enabled:!!org&&staff&&!platform,refetchInterval:30000});
 // Independent clinical practice and patient self-service keep their existing backend guards.
 return <Context.Provider value={platform?undefined:org&&staff?(access.isError?undefined:access.data):null}>{children}</Context.Provider>;
}
export function HospitalOperationBoundary({children}:{children:ReactNode}){
 const access=useContext(Context);const location=useLocation();const {i18n}=useTranslation();const auth=useAuth();const requirement=pathPermission(location.pathname);
 if(!requirement)return <>{children}</>;
 if(auth.roles.includes('PLATFORM_ADMIN'))return <p role="alert">{i18n.language.startsWith('fr')?'Les opérations des établissements sont réservées à leurs équipes.':'Organization operations are reserved for their teams.'}</p>;
 if(access!==null&&!allowsPermission(access,requirement.resource,requirement.action))return <p role="alert">{i18n.language.startsWith('fr')?'Accès non autorisé pour cet établissement. Sélectionnez un établissement et vérifiez vos droits.':'Access denied for this organization. Select an organization and verify your permissions.'}</p>;
 return <>{children}</>;
}
