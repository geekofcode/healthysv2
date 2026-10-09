// @vitest-environment jsdom
import {afterEach,describe,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter} from 'react-router-dom';
vi.mock('../auth/AuthContext',()=>({useAuth:()=>({hasAnyRole:(...roles:string[])=>roles.includes('DOCTOR')})}));
vi.mock('../auth/keycloak',()=>({keycloak:{tokenParsed:{sub:'doctor'}}}));
vi.mock('../auth/organizationContext',()=>({selectedOrganization:vi.fn(()=> 'independent')}));
vi.mock('react-i18next',()=>({useTranslation:()=>({t:(key:string)=>key,i18n:{language:'fr'}})}));
vi.mock('../api/client',()=>({ApiError:class extends Error{}}));
vi.mock('../api/patients',()=>({listPatients:vi.fn().mockResolvedValue({content:[{id:'patient',patientNumber:'PAT-001'}]})}));
vi.mock('../api/professionalOnboarding',()=>({getMyProfessional:vi.fn().mockResolvedValue({id:'professional',professionalType:'DOCTOR'})}));
vi.mock('../api/consultations',()=>({startConsultation:vi.fn().mockResolvedValue({id:'consultation'})}));
vi.mock('../api/pharmacy',()=>({createPrescription:vi.fn(),listMedicationCatalog:vi.fn().mockResolvedValue([]),listPrescriptions:vi.fn().mockResolvedValue({content:[]}),pharmacyKeys:{prescriptions:(filters:unknown)=>['prescriptions',filters],catalog:(query:string)=>['catalog',query]},validPrescriptionItem:vi.fn()}));
import {selectedOrganization} from '../auth/organizationContext';
import {startConsultation} from '../api/consultations';
import {listPrescriptions} from '../api/pharmacy';
import {ConsultationStartPage} from './ConsultationStartPage';
import {PrescriptionsPage} from './PrescriptionsPage';
afterEach(()=>{cleanup();vi.clearAllMocks();vi.mocked(selectedOrganization).mockReturnValue('independent');});
function show(page:React.ReactNode){render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><MemoryRouter>{page}</MemoryRouter></QueryClientProvider>);}
describe('independent clinical workflows',()=>{it('requires patient selection for a doctor even in organization context',async()=>{vi.mocked(selectedOrganization).mockReturnValue('organization');show(<PrescriptionsPage/>);await screen.findByRole('option',{name:'PAT-001'});expect(listPrescriptions).not.toHaveBeenCalled();fireEvent.change(screen.getByRole('combobox',{name:'pharmacy.patientId'}),{target:{value:'patient'}});fireEvent.click(screen.getByRole('button',{name:'pharmacy.search'}));await waitFor(()=>expect(listPrescriptions).toHaveBeenCalledWith({patientId:'patient',organizationId:'organization'}));});it('uses the authenticated professional and allows consultation without organization',async()=>{show(<ConsultationStartPage/>);await screen.findByRole('option',{name:'PAT-001'});expect(screen.queryByLabelText('consultation.professionalId')).toBeNull();expect(screen.queryByLabelText('consultation.organizationId')).toBeNull();fireEvent.change(screen.getByLabelText('consultation.patientId'),{target:{value:'patient'}});fireEvent.click(screen.getByRole('button',{name:'consultation.startAction'}));await waitFor(()=>expect(startConsultation).toHaveBeenCalledWith(expect.objectContaining({patientId:'patient',professionalId:'professional',organizationId:undefined})));});it('does not fetch a global prescription list before independent patient selection',async()=>{show(<PrescriptionsPage/>);await screen.findByRole('option',{name:'PAT-001'});expect(listPrescriptions).not.toHaveBeenCalled();fireEvent.change(screen.getByRole('combobox',{name:'pharmacy.patientId'}),{target:{value:'patient'}});fireEvent.click(screen.getByRole('button',{name:'pharmacy.search'}));await waitFor(()=>expect(listPrescriptions).toHaveBeenCalledWith({patientId:'patient',organizationId:undefined}));fireEvent.click(screen.getByRole('button',{name:'pharmacy.newPrescription'}));expect(screen.queryByLabelText('pharmacy.prescriberId')).toBeNull();expect(screen.queryByLabelText('pharmacy.organizationId')).toBeNull();});});
