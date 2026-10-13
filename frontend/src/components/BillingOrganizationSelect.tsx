import {useQuery} from '@tanstack/react-query';
import {listMyMemberships} from '../api/licensing';
import {useAuth} from '../auth/AuthContext';
import {ClinicalReferenceSelect} from './ClinicalReferenceSelect';
import {ApiErrorMessage} from './ApiErrorMessage';
export function BillingOrganizationSelect({value,onChange,label}:{value:string;onChange:(id:string)=>void;label:string}){
 const auth=useAuth();const admin=auth.hasAnyRole('PLATFORM_ADMIN');
 const memberships=useQuery({queryKey:['billing-memberships'],queryFn:listMyMemberships,enabled:!admin});
 if(admin)return <ClinicalReferenceSelect kind="organization" label={label} value={value} onChange={onChange}/>;
 return <><label>{label}<select value={value} onChange={event=>onChange(event.target.value)}><option value="">—</option>{memberships.data?.map(member=><option key={member.organizationId} value={member.organizationId}>{member.organizationName??member.organizationId}</option>)}</select></label>{memberships.error&&<ApiErrorMessage error={memberships.error}/>}</>;
}
