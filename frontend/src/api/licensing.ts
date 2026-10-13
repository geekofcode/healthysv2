import {apiRequest} from './client';
export type PlanInput={name:string;amountMinor:number;currency:string;interval:'MONTH'|'YEAR';doctorQuota:number;professionalQuota:number;cashierQuota:number;accountantQuota:number;aiAssistantEnabled:boolean;stripePriceId:string;active:boolean};
export type Plan=PlanInput&{id:string};
export type Entitlements={status:string;active:boolean;aiAssistantEnabled:boolean;planName?:string;doctorQuota:number;professionalQuota:number;cashierQuota:number;accountantQuota:number;usage:Record<string,number>;currentPeriodEnd?:string};
export type TeamMember={id:string;personId?:string;email?:string;role:string;status:string};
export type SubscriptionInvoice={id:string;amountMinor:number;currency:string;status:string;invoiceUrl?:string;periodStart?:string;periodEnd?:string};
export const licensingKeys={all:['licensing'] as const,plans:['licensing','plans'] as const,organization:(id:string)=>['licensing',id] as const};
function camelize(value:any):any{if(Array.isArray(value))return value.map(camelize);if(value&&typeof value==='object')return Object.fromEntries(Object.entries(value).map(([k,v])=>[k.replace(/_([a-z])/g,(_,c:string)=>c.toUpperCase()),camelize(v)]));return value;}
async function request<T>(path:string,init?:RequestInit):Promise<T>{return camelize(await apiRequest(path,init));}
const base=(id:string)=>`/licensing/organizations/${id}`;
export const listPlans=async()=>{const rows=await request<(Plan&{billingInterval?:PlanInput['interval']})[]>('/licensing/plans');return rows.map(p=>({...p,interval:p.interval??p.billingInterval??'MONTH'}));};
export const savePlan=(plan:PlanInput,id?:string)=>request<Plan>(`/licensing/plans${id?`/${id}`:''}`,{method:id?'PUT':'POST',body:JSON.stringify(plan)});
export const getEntitlements=async(id:string)=>{const data=await request<Entitlements&{periodEnd?:string}>(`${base(id)}/entitlements`);return {...data,currentPeriodEnd:data.periodEnd??data.currentPeriodEnd};};
export const listTeam=(id:string)=>request<TeamMember[]>(`${base(id)}/team`);
export const addTeam=(id:string,input:{personId?:string;email?:string;role:string})=>request<TeamMember>(`${base(id)}/team`,{method:'POST',body:JSON.stringify(input)});
export const updateTeam=(org:string,id:string,input:{role:string;status:string})=>request<TeamMember>(`${base(org)}/team/${id}`,{method:'PUT',body:JSON.stringify(input)});
export const acceptTeam=(org:string,id:string)=>request<TeamMember>(`${base(org)}/team/${id}/accept`,{method:'POST'});
export const checkout=(org:string,planId:string)=>request<{url:string}>(`${base(org)}/checkout`,{method:'POST',body:JSON.stringify({planId})});
export const portal=(org:string)=>request<{url:string}>(`${base(org)}/portal`,{method:'POST'});
export const listSubscriptionInvoices=async(org:string)=>{const rows=await request<(SubscriptionInvoice&{amountPaid?:number;hostedInvoiceUrl?:string})[]>(`${base(org)}/invoices`);return rows.map(v=>({...v,amountMinor:v.amountPaid??v.amountMinor,invoiceUrl:v.hostedInvoiceUrl??v.invoiceUrl}));};
export function openPaymentUrl(url:string){const parsed=new URL(url);if(parsed.protocol!=='https:')throw new Error('Invalid payment URL');window.location.assign(parsed.href);}
export type Membership={organizationId:string;organizationName:string;role:string};
export type TeamInvitation={id:string;organizationId:string;organizationName:string;role:string;email:string;expiresAt:string};
export const listMyMemberships=()=>request<Membership[]>('/licensing/my-memberships');
export const listMyTeamInvitations=()=>request<TeamInvitation[]>('/licensing/my-invitations');

export function formatSubscriptionMoney(amountMinor:number,currency:string,locale:string){
 const format=new Intl.NumberFormat(locale,{style:'currency',currency:currency.toUpperCase()});
 const digits=format.resolvedOptions().maximumFractionDigits??2;
 return format.format(amountMinor/(10**digits));
}
