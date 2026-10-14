import {createContext,useContext} from 'react';
import type {AccessAction,HospitalAccess} from '../api/hospitalAccess';
export type PermissionRequirement={resource:string;action:AccessAction};
export function pathPermission(path:string):PermissionRequirement|undefined{
 const s=path.split('/').filter(Boolean);let resource:string|undefined;
 if(s[0]==='employees')resource=s[1]==='roles'?'roles':'employees';
 if(s[0]==='organizations'&&s[2]==='employees')resource='employees';
 if(s[0]==='organizations'&&s[2]==='roles')resource='roles';
 if(s[0]==='patients')resource=s[2]==='access'?'patientaccess':s.length===1||s[1]==='new'||s[2]==='identity'||s[2]==='edit'?'patients':'clinicalrecords';
 if(s[0]==='care')resource=s[1]==='visits'?'reports':'episodes';
 if(s[0]==='professionals')resource='employees';
 if(s[0]==='agenda')resource='scheduling';
 if(s[0]==='consultations')resource='reports';
 if(s[0]==='documents')resource='documents';
 if(s[0]==='billing')resource=s[1]==='tariffs'?'tariffs':s[1]==='cash'?'cashreports':'invoices';
 if(!resource)return;
 return {resource,action:s.includes('new')?'CREATE':s.includes('edit')?'UPDATE':'READ'};
}
export function allowsPermission(access:HospitalAccess|undefined,resource:string,action:AccessAction){return !!access&&(access.administrator||access.permissions.some(p=>p.resource===resource&&p.actions.includes(action)));}
export const HospitalPermissionContext=createContext<HospitalAccess|undefined|null>(null);
export function useHospitalPermission(resource:string,action:AccessAction){const access=useContext(HospitalPermissionContext);return access===null?true:allowsPermission(access,resource,action);}
