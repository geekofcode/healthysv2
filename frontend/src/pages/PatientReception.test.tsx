vi.mock('../components/DatedMeasurements',()=>({DatedMeasurements:()=>null,measurementsAsInstants:(value:unknown)=>value}));
// @vitest-environment jsdom
import {afterEach,describe,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter,Route,Routes} from 'react-router-dom';
vi.mock('../api/client',()=>({ApiError:class extends Error{}}));
vi.mock('../api/professionals',()=>({listSpecialityCatalog:vi.fn().mockResolvedValue([])}));
vi.mock('react-i18next',()=>({useTranslation:()=>({t:(k:string)=>k,i18n:{language:'fr'}})}));
vi.mock('../auth/AuthContext',()=>({useAuth:()=>({hasAnyRole:(...roles:string[])=>roles.includes('NURSE')})}));
vi.mock('../api/patientIdentities',()=>({identityKeys:{all:['identities'],detail:(id:string)=>['identity',id]},getPatientIdentity:vi.fn().mockResolvedValue({id:'p1',personId:'person',patientNumber:'PAT-1',firstName:'Marie',lastName:'Test',status:'ACTIVE',identifiers:[]}),updatePatientIdentity:vi.fn(),createPatientIdentity:vi.fn(),searchPatientIdentities:vi.fn()}));
vi.mock('../api/patients',()=>({getPatient:vi.fn()}));
vi.mock('../components/PersonPicker',()=>({PersonPicker:()=>null}));
vi.mock('../api/careEpisodes',()=>({listCareEpisodes:vi.fn().mockResolvedValue([]),getReceptionOptions:vi.fn().mockResolvedValue({specialities:[{id:'spec',name:'Généraliste'}],doctors:[{professionalId:'doctor',firstName:'Jean',lastName:'Médecin',specialityIds:['spec']}]}),getCareEpisode:vi.fn().mockResolvedValue({id:'episode',patientId:'p1',status:'AWAITING_PAYMENT',reason:'Fièvre',visits:[{id:'v1',status:'PENDING_PAYMENT'}]}),getCareIntake:vi.fn().mockResolvedValue({observations:'',vitals:{}}),saveCareIntake:vi.fn().mockResolvedValue({observations:'Accueil',measurements:[],vitals:{temperatureC:38.5}}),createCareEpisode:vi.fn().mockResolvedValue({id:'new-episode'})}));
import {PatientIdentityPage} from './PatientIdentityPage';
import {CareEpisodeCreatePage,CareEpisodePage} from './CareEpisodePage';
import {getPatient} from '../api/patients';
import {createCareEpisode,saveCareIntake} from '../api/careEpisodes';
afterEach(()=>{cleanup();vi.clearAllMocks();});
function show(path:string,route:string,element:React.ReactNode){render(<MemoryRouter initialEntries={[path]}><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><Routes><Route path={route} element={element}/></Routes></QueryClientProvider></MemoryRouter>);}
describe('patient reception',()=>{
 it('loads identity without fetching the clinical record and links to reception',async()=>{show('/patients/p1/identity','/patients/:id/identity',<PatientIdentityPage/>);await screen.findByText('Marie Test');expect(getPatient).not.toHaveBeenCalled();expect(screen.getByRole('link',{name:'Ouvrir une prise en charge'}).getAttribute('href')).toBe('/care/episodes/new?patientId=p1');});
 it('creates an episode with the selected doctor, speciality and visit date',async()=>{show('/care/episodes/new?patientId=p1','/care/episodes/new',<CareEpisodeCreatePage/>);fireEvent.change(screen.getByLabelText('Motif de consultation'),{target:{value:'Fièvre'}});await screen.findByRole('option',{name:'Généraliste'});fireEvent.change(screen.getByLabelText('Spécialité'),{target:{value:'spec'}});fireEvent.change(screen.getByLabelText('Médecin en charge'),{target:{value:'doctor'}});fireEvent.change(screen.getByLabelText('Date et heure de la première visite'),{target:{value:'2026-10-15T10:00'}});fireEvent.click(screen.getByRole('button',{name:'Ouvrir l’épisode et la première visite'}));await waitFor(()=>expect(createCareEpisode).toHaveBeenCalledWith({patientId:'p1',reason:'Fièvre',specialityId:'spec',professionalId:'doctor',scheduledAt:new Date('2026-10-15T10:00').toISOString()}));});
 it('saves nursing intake before the medical follow-up period starts',async()=>{show('/care/episodes/episode','/care/episodes/:id',<CareEpisodePage/>);await screen.findByText('Suivi non démarré : première rencontre médicale à venir.');fireEvent.change(await screen.findByLabelText('Température (°C)'),{target:{value:'38.5'}});fireEvent.change(screen.getByLabelText('Observations d’accueil'),{target:{value:'Accueil'}});fireEvent.click(screen.getByRole('button',{name:'Enregistrer l’accueil'}));await waitFor(()=>expect(saveCareIntake).toHaveBeenCalledWith('episode',{observations:'Accueil',measurements:[],vitals:{temperatureC:38.5}}));});
});
