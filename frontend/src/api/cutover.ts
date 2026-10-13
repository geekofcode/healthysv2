import {apiRequest} from './client';
export type CareCutover={mode:'LEGACY'|'PILOT'|'CARE_ONLY';migrationValidated:boolean;journeyValidated:boolean;restorationValidated:boolean};
export const getCareCutover=()=>apiRequest<CareCutover>('/care/cutover');
