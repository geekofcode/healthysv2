// @vitest-environment jsdom
import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter} from 'react-router-dom';
const mock=vi.hoisted(()=>({create:vi.fn().mockResolvedValue({id:'family'}),me:vi.fn().mockResolvedValue({firstName:'Parent',lastName:'Dupont'})}));
vi.mock('../auth/AuthContext',()=>({useAuth:()=>({username:'parent'})}));
vi.mock('../api/client',()=>({ApiError:class extends Error{}}));
vi.mock('react-i18next',()=>({useTranslation:()=>({i18n:{language:'fr'}})}));
vi.mock('../api/patientProfiles',()=>({createPersonalPatientProfile:mock.create}));
vi.mock('../api/persons',()=>({getMe:mock.me}));
import {PatientProfileCreatePage} from './PatientProfileCreatePage';
afterEach(()=>{cleanup();vi.clearAllMocks()});
function mount(){render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><MemoryRouter><PatientProfileCreatePage/></MemoryRouter></QueryClientProvider>);}
it('offers patient creation only and never professional or administrative profile self-creation',async()=>{mount();await waitFor(()=>expect((screen.getByRole('button',{name:'Créer le profil patient'}) as HTMLButtonElement).disabled).toBe(false));expect(screen.getAllByRole('option').map(option=>option.textContent)).not.toContain('Médecin');expect(screen.queryByRole('option',{name:'Administrateur'})).toBeNull();expect(screen.getByRole('option',{name:'Moi-même'})).toBeTruthy();expect(screen.getByRole('option',{name:'Un enfant mineur'})).toBeTruthy();});
it('sends child identity and adult guardian relationship/date of birth with an idempotency key',async()=>{mount();await waitFor(()=>expect((screen.getByRole('button',{name:'Créer le profil patient'}) as HTMLButtonElement).disabled).toBe(false));fireEvent.change(screen.getByLabelText('Profil pour'),{target:{value:'MINOR'}});fireEvent.change(screen.getByLabelText('Prénom du patient'),{target:{value:'Alice'}});fireEvent.change(screen.getByLabelText('Nom du patient'),{target:{value:'Dupont'}});fireEvent.change(screen.getByLabelText('Date de naissance du patient'),{target:{value:'2020-02-03'}});fireEvent.change(screen.getByLabelText('Votre date de naissance'),{target:{value:'1980-01-02'}});fireEvent.change(screen.getByLabelText('Lien avec le patient'),{target:{value:'GUARDIAN'}});fireEvent.click(screen.getByRole('button',{name:'Créer le profil patient'}));await waitFor(()=>expect(mock.create).toHaveBeenCalledWith({kind:'MINOR',firstName:'Alice',lastName:'Dupont',birthDate:'2020-02-03',gender:undefined,guardianRelationship:'GUARDIAN',guardianBirthDate:'1980-01-02'},expect.any(String)));});
