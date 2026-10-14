// @vitest-environment jsdom
import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter,Routes,Route} from 'react-router-dom';
const mocks=vi.hoisted(()=>({ent:vi.fn(),add:vi.fn().mockResolvedValue({}),change:vi.fn().mockResolvedValue({}),checkout:vi.fn().mockResolvedValue({url:'https://checkout.stripe.com/session'}),redirect:vi.fn(),platform:false,team:vi.fn().mockResolvedValue([])}));
vi.mock('../api/client',()=>({ApiError:class extends Error{}}));
vi.mock('react-i18next',()=>({useTranslation:()=>({i18n:{language:'fr'}})}));
vi.mock('../auth/AuthContext',()=>({useAuth:()=>({hasAnyRole:(...roles:string[])=>roles.includes(mocks.platform?'PLATFORM_ADMIN':'HOSPITAL_ADMIN')})}));
vi.mock('../api/licensing',()=>({formatSubscriptionMoney:(amount:number,currency:string)=>`${amount} ${currency}`,licensingKeys:{plans:['licensing','plans'],organization:(id:string)=>['licensing',id]},getEntitlements:mocks.ent,listPlans:vi.fn().mockResolvedValue([{id:'p',name:'Pro',amountMinor:2000,currency:'CAD',interval:'MONTH',active:true}]),listTeam:mocks.team,listSubscriptionInvoices:vi.fn().mockResolvedValue([]),addTeam:mocks.add,updateTeam:mocks.change,checkout:mocks.checkout,portal:vi.fn(),openPaymentUrl:mocks.redirect}));
import {OrganizationLicensePage} from './OrganizationLicensePage';
function mount(){render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><MemoryRouter initialEntries={['/organizations/o/license']}><Routes><Route path="/organizations/:id/license" element={<OrganizationLicensePage/>}/></Routes></MemoryRouter></QueryClientProvider>)}
afterEach(()=>{cleanup();vi.clearAllMocks()});
it('blocks additions and AI for inactive license while allowing Stripe checkout',async()=>{mocks.ent.mockResolvedValue({active:false,status:'expired',aiAssistantEnabled:true,usage:{},doctorQuota:1});mount();await screen.findByText(/Licence inactive/);expect(screen.getByText('Assistant IA: Indisponible')).toBeTruthy();expect(screen.queryByLabelText('Email d’invitation')).toBeNull();expect(mocks.team).not.toHaveBeenCalled();fireEvent.change(screen.getByLabelText('Offre d’abonnement'),{target:{value:'p'}});fireEvent.click(screen.getByRole('button',{name:'Souscrire via Stripe'}));await waitFor(()=>expect(mocks.checkout).toHaveBeenCalledWith('o','p'));});
it('never loads organization staff for platform administration',async()=>{mocks.platform=true;mocks.ent.mockResolvedValue({active:true,status:'active',aiAssistantEnabled:false,usage:{doctor:1},doctorQuota:2});mount();await screen.findByText(/Licence active/);expect(mocks.team).not.toHaveBeenCalled();expect(screen.queryByText('Gestion des employés')).toBeNull();expect(screen.queryByRole('button',{name:'Souscrire via Stripe'})).toBeNull();});
