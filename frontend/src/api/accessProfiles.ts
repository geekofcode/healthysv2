import {apiRequest} from './client';
export type AccessProfile={id:string;personId:string;type:string;sourceType:string;sourceId?:string;organizationId?:string|null;organizationName?:string|null;label:string;active:boolean};
export const listAccessProfiles=()=>apiRequest<AccessProfile[]>('/me/access-profiles');
