// @vitest-environment jsdom
import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
const mocks=vi.hoisted(()=>({save:vi.fn().mockResolvedValue({id:'p1'})}));
vi.mock('../api/client',()=>({ApiError:class extends Error{}}));
vi.mock('react-i18next',()=>({useTranslation:()=>({i18n:{language:'fr'}})}));
vi.mock('../api/licensing',()=>({formatSubscriptionMoney:(amount:number,currency:string)=>`${amount} ${currency}`,licensingKeys:{plans:['licensing','plans'],all:['licensing']},listPlans:vi.fn().mockResolvedValue([]),savePlan:mocks.save}));
import {LicensingPlansPage} from './LicensingPlansPage';
afterEach(()=>{cleanup();mocks.save.mockClear()});
it('saves quotas and optional AI with a Stripe price',async()=>{render(<QueryClientProvider client={new QueryClient()}><LicensingPlansPage/></QueryClientProvider>);fireEvent.change(screen.getByLabelText('Nom'),{target:{value:'Clinique'}});fireEvent.change(screen.getByLabelText('Prix en unité monétaire minimale'),{target:{value:'2500'}});fireEvent.change(screen.getByLabelText('Médecins'),{target:{value:'3'}});fireEvent.change(screen.getByLabelText('Référence tarif Stripe'),{target:{value:'price_123'}});fireEvent.click(screen.getByLabelText('Assistant IA inclus'));fireEvent.click(screen.getByRole('button',{name:'Enregistrer'}));await waitFor(()=>expect(mocks.save).toHaveBeenCalledWith(expect.objectContaining({name:'Clinique',amountMinor:2500,doctorQuota:3,aiAssistantEnabled:true,stripePriceId:'price_123'}),undefined));});
