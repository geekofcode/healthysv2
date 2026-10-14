// @vitest-environment jsdom
import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {MemoryRouter,Route,Routes} from 'react-router-dom';
const mock=vi.hoisted(()=>({choose:vi.fn().mockResolvedValue(undefined),profiles:[{id:'p',label:'Patient',type:'PATIENT'}]}));
vi.mock('../auth/AuthContext',()=>({useAuth:()=>({profiles:mock.profiles,switchProfile:mock.choose,logout:vi.fn()})}));
vi.mock('../api/client',()=>({ApiError:class extends Error{}}));
vi.mock('react-i18next',()=>({useTranslation:()=>({i18n:{language:'fr'}})}));
import {ProfileSelectionPage} from './ProfileSelectionPage';
afterEach(()=>{cleanup();vi.clearAllMocks();mock.profiles=[{id:'p',label:'Patient',type:'PATIENT'}];});
it('requires clicking even a single profile before its contract opens',async()=>{render(<MemoryRouter initialEntries={['/profiles/select']}><Routes><Route path="/profiles/select" element={<ProfileSelectionPage/>}/><Route path="/profiles/p/contract" element={<p>Contrat patient</p>}/></Routes></MemoryRouter>);expect(mock.choose).not.toHaveBeenCalled();fireEvent.click(screen.getByRole('button',{name:'Patient'}));await waitFor(()=>expect(mock.choose).toHaveBeenCalledWith('p'));await screen.findByText('Contrat patient');});
it('offers only patient self-creation and does not show a generic account as a workspace',()=>{mock.profiles=[{id:'account',label:'Mon compte',type:'ACCOUNT'}];render(<MemoryRouter><ProfileSelectionPage/></MemoryRouter>);expect(screen.queryByRole('button',{name:'Mon compte'})).toBeNull();expect(screen.getByRole('link',{name:'Créer un profil patient'}).getAttribute('href')).toBe('/profiles/patient/new');expect(screen.queryByRole('link',{name:'Créer un médecin'})).toBeNull();});
