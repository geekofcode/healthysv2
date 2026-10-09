import {apiRequest} from './client';
export type PersonPreferences = {theme:'LIGHT'|'DARK'|'SYSTEM';avatarUrl:string|null};
export const getPreferences = () => apiRequest<PersonPreferences>('/persons/me/preferences');
export const updatePreferences = (preferences:PersonPreferences) => apiRequest<PersonPreferences>('/persons/me/preferences',{method:'PUT',body:JSON.stringify(preferences)});
