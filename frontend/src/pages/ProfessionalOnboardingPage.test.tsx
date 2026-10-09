// @vitest-environment jsdom
import {afterEach,describe,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
vi.mock('../api/client',()=>({ApiError:class extends Error{}}));
vi.mock('../auth/AuthContext',()=>({useAuth:()=>({login:vi.fn()})}));
vi.mock('react-i18next',()=>({useTranslation:()=>({t:(key:string)=>key})}));
vi.mock('../api/professionalOnboarding',()=>({getMyDossier:vi.fn(),listCountries:vi.fn().mockResolvedValue([{id:'cm',name:'Cameroun'}]),listSpecialities:vi.fn().mockResolvedValue([]),saveDossier:vi.fn(),uploadProof:vi.fn(),submitDossier:vi.fn(),onboardingKeys:{me:['onboarding','me']}}));
import {getMyDossier,uploadProof} from '../api/professionalOnboarding';
import {ProfessionalOnboardingPage} from './ProfessionalOnboardingPage';
afterEach(()=>{cleanup();vi.clearAllMocks();});
function show(){render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><ProfessionalOnboardingPage/></QueryClientProvider>);}
describe('professional application',()=>{it('allows an independent draft without an organization field',async()=>{vi.mocked(getMyDossier).mockResolvedValue(null);show();await screen.findByRole('heading',{name:'onboarding.title'});expect(screen.queryByLabelText('professionals.organizationId')).toBeNull();expect(screen.getByText('onboarding.independent')).toBeTruthy();expect(screen.getByLabelText('onboarding.country').tagName).toBe('SELECT');});it('disables submission until evidence is uploaded and rejects oversized evidence',async()=>{vi.mocked(getMyDossier).mockResolvedValue({id:'id',personId:'p',keycloakUserId:'k',profession:'medecin',licenseNumber:'ABC',issuingAuthority:'Order',countryId:'cm',status:'DRAFT',proofUploaded:false,createdAt:'now',updatedAt:'now'});show();await screen.findByRole('heading',{name:'onboarding.title'});expect(screen.getByRole('button',{name:'onboarding.submit'}).hasAttribute('disabled')).toBe(true);const oversized=new File([new Uint8Array(5*1024*1024+1)],'proof.pdf',{type:'application/pdf'});fireEvent.change(screen.getByLabelText('onboarding.proof'),{target:{files:[oversized]}});await waitFor(()=>expect(screen.getByRole('alert')).toBeTruthy());expect(uploadProof).not.toHaveBeenCalled();});});
