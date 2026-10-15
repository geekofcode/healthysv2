import type {HospitalAccess} from '../api/hospitalAccess';
import {allowsPermission} from './HospitalPermissions';
export type ProfileNavItem={path:string;label:string};
export function profileNavigation(type:string,access?:HospitalAccess|null,organizationId?:string):ProfileNavItem[]{
 const base:ProfileNavItem[]=[{path:'/',label:'home'},{path:'/me',label:'profile'}];
 if(type==='PATIENT')return [...base,{path:'/consultations',label:'consultations'},{path:'/agenda',label:'agenda'},{path:'/documents',label:'documents'},{path:'/billing',label:'invoices'},{path:'/me/access',label:'access'}];
 if(type==='PLATFORM_ADMIN')return [...base,{path:'/organizations',label:'organizations'},{path:'/admin/licensing-plans',label:'plans'},{path:'/admin/subscriptions',label:'subscriptions'},{path:'/admin/accounts',label:'accounts'},{path:'/admin/platform-audit',label:'audit'},{path:'/admin/contract-templates',label:'contracts'}];
 const allowed=(resource:string)=>allowsPermission(access??undefined,resource,'READ');
 const operations:ProfileNavItem[]=[];
 if(allowed('employees'))operations.push({path:'/employees',label:'employees'});
 if(allowed('roles'))operations.push({path:'/employees/roles',label:'roles'});
 if(allowed('patients'))operations.push({path:'/patients',label:'reception'});
 if(allowed('reports'))operations.push({path:'/consultations',label:'consultations'});
 if(allowed('scheduling')||allowed('queue'))operations.push({path:'/agenda',label:'calendarQueue'});
 if(allowed('documents'))operations.push({path:'/documents',label:'documents'});
 if(allowed('invoices'))operations.push({path:'/billing',label:'invoices'});
 if(allowed('cashreports'))operations.push({path:'/billing/cash',label:'cash'});
 if(allowed('tariffs'))operations.push({path:'/billing/tariffs',label:'tariffs'},{path:'/billing/speciality-policies',label:'policies'});
 if(type==='HOSPITAL_ADMIN'&&organizationId)operations.push({path:`/organizations/${organizationId}/license`,label:'subscription'});
 return [...base,...operations];
}
