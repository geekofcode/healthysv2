// @vitest-environment jsdom
import {afterEach,describe,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter} from 'react-router-dom';
vi.mock('react-i18next',()=>({useTranslation:()=>({i18n:{language:'fr'}})}));
vi.mock('../api/client',()=>({ApiError:class extends Error{}}));
vi.mock('../auth/AuthContext',()=>({useAuth:()=>({hasAnyRole:(...r:string[])=>r.includes('NURSE')})}));
vi.mock('../api/patientAccess',()=>({listAccessContexts:vi.fn().mockResolvedValue([{personId:'doctor',personName:'Dr Paul',organizationId:'hospital',organizationName:'Hôpital',expiresAt:'2099-10-13T00:00:00Z'}]),listAccessRequests:vi.fn().mockResolvedValue([{id:'request',requesterName:'Dr Marie',organizationName:'Clinique',reason:'Suivi',status:'PENDING',createdAt:'2026-10-13T00:00:00Z'}]),listAccessGrants:vi.fn().mockResolvedValue([]),acceptAccessRequest:vi.fn().mockResolvedValue({}),revokeAccessRequest:vi.fn(),revokeAccessGrant:vi.fn(),requestPatientAccess:vi.fn(),emergencyPatientAccess:vi.fn(),revokeDerivedAccess:vi.fn()}));
import {PatientAccessPage} from './PatientAccessPage';
import {acceptAccessRequest,emergencyPatientAccess,revokeDerivedAccess,listAccessContexts} from '../api/patientAccess';
afterEach(()=>{cleanup();vi.clearAllMocks();});
function show(self:boolean){render(<MemoryRouter><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><PatientAccessPage patientId="patient" patientSelf={self}/></QueryClientProvider></MemoryRouter>);}
describe('temporary access interface',()=>{
 it('lets the owner approve a named requester and hides emergency controls',async()=>{show(true);await screen.findByText('Dr Marie · Clinique');fireEvent.click(screen.getByRole('button',{name:'Autoriser'}));await waitFor(()=>expect(acceptAccessRequest).toHaveBeenCalledWith('patient','request'));expect(screen.queryByRole('button',{name:'Demander un accès d’urgence'})).toBeNull();});
 it('lets the owner revoke visit-derived access without an explicit grant row',async()=>{show(true);await screen.findByText('Dr Paul · Hôpital');fireEvent.click(screen.getByRole('button',{name:'Révoquer les accès liés à cette prise en charge'}));await waitFor(()=>expect(revokeDerivedAccess).toHaveBeenCalledWith('patient','doctor','hospital'));});
 it('does not let staff self-approve and requires emergency justification',async()=>{show(false);await screen.findByText('Dr Marie · Clinique');expect(screen.queryByRole('button',{name:'Autoriser'})).toBeNull();expect(listAccessContexts).not.toHaveBeenCalled();fireEvent.click(screen.getByRole('button',{name:'Demander un accès d’urgence'}));const activate=screen.getByRole('button',{name:'Activer l’accès d’urgence'}) as HTMLButtonElement;expect(activate.disabled).toBe(true);fireEvent.change(screen.getByLabelText('Justification de l’urgence'),{target:{value:'Détresse respiratoire'}});fireEvent.click(activate);await waitFor(()=>expect(emergencyPatientAccess).toHaveBeenCalledWith('patient','Détresse respiratoire'));});
});
