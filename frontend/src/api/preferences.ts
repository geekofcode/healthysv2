import {apiRequest} from './client';
export type PersonPreferences = {theme:'LIGHT'|'DARK'|'SYSTEM';avatarUrl:string|null;language?:'SYSTEM'|'FR'|'EN'};
export const getPreferences = () => apiRequest<PersonPreferences>('/persons/me/preferences');
export const updatePreferences = (preferences:Partial<PersonPreferences>) => apiRequest<PersonPreferences>('/persons/me/preferences',{method:'PUT',body:JSON.stringify(preferences)});
