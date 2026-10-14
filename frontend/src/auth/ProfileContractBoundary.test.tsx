// @vitest-environment jsdom
import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,render,screen} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter,Route,Routes} from 'react-router-dom';
const mock=vi.hoisted(()=>({state:vi.fn().mockResolvedValue({profileId:'p',signed:false}),workspace:vi.fn(),platform:false}));
vi.mock('./AuthContext',()=>({useAuth:()=>({activeProfile:{id:'p'},hasAnyRole:()=>mock.platform})}));
vi.mock('../api/client',()=>({ApiError:class extends Error{}}));
vi.mock('../api/profileContracts',()=>({getProfileContract:mock.state}));
vi.mock('react-i18next',()=>({useTranslation:()=>({i18n:{language:'fr'}})}));
import {ProfileContractBoundary} from './ProfileContractBoundary';
function Workspace(){mock.workspace();return <p>Opérations médicales</p>;}
afterEach(()=>{cleanup();vi.clearAllMocks();mock.platform=false;mock.state.mockResolvedValue({profileId:'p',signed:false});});
function mount(path='/'){render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><MemoryRouter initialEntries={[path]}><Routes><Route path="/" element={<ProfileContractBoundary><Workspace/></ProfileContractBoundary>}/><Route path="/admin/contract-templates" element={<ProfileContractBoundary><p>Configuration des contrats</p></ProfileContractBoundary>}/><Route path="/profiles/p/contract" element={<p>Signature obligatoire</p>}/></Routes></MemoryRouter></QueryClientProvider>);}
it('does not mount any workspace before the selected profile’s contract is signed',async()=>{mount();await screen.findByText('Signature obligatoire');expect(mock.workspace).not.toHaveBeenCalled();expect(screen.queryByText('Opérations médicales')).toBeNull();});
it('allows workspace after a signed contract even while PDF/email processing is pending',async()=>{mock.state.mockResolvedValue({profileId:'p',signed:true,pdfReady:false,emailStatus:'QUEUED'});mount();await screen.findByText('Opérations médicales');expect(mock.workspace).toHaveBeenCalled();});
it('allows an unsigned platform administrator to configure genuine unpublished templates',async()=>{mock.platform=true;mount('/admin/contract-templates');await screen.findByText('Configuration des contrats');expect(mock.state).not.toHaveBeenCalled();});
