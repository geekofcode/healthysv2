// @vitest-environment jsdom
import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
const mocks=vi.hoisted(()=>({schedule:vi.fn().mockResolvedValue({})}));
vi.mock('../api/client',()=>({ApiError:class extends Error{}}));
vi.mock('react-i18next',()=>({useTranslation:()=>({t:(key:string)=>key})}));
vi.mock('../api/careEpisodes',()=>({getReceptionOptions:vi.fn().mockResolvedValue({doctors:[{professionalId:'doctor1',firstName:'Jean',lastName:'A',specialityIds:['s']},{professionalId:'doctor2',firstName:'Paul',lastName:'B',specialityIds:['s']}]}),getScheduledCareVisit:vi.fn().mockResolvedValue({history:[{id:'h',changedAt:'2026-10-13T09:00:00Z',status:'SCHEDULED',reason:'Médecin indisponible'}]}),scheduleCareVisit:mocks.schedule}));
import {VisitScheduleForm} from './VisitScheduleForm';
afterEach(()=>{cleanup();vi.clearAllMocks();});
it('displays persistent history and changes doctor on same visit with version',async()=>{
 render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><VisitScheduleForm episodeId="episode" visit={{id:'visit',professionalId:'doctor1',scheduledAt:'2026-10-14T10:00:00Z',status:'SCHEDULED',followUp:false,version:4}} specialityId="s" fr/></QueryClientProvider>);
 await screen.findByText(/Médecin indisponible/);
 await screen.findByRole('option',{name:'Paul B'});
 fireEvent.change(screen.getByLabelText('Médecin'),{target:{value:'doctor2'}});
 fireEvent.change(screen.getByLabelText('Motif du changement'),{target:{value:'Nouvelle affectation'}});
 fireEvent.click(screen.getByRole('button',{name:'Enregistrer la programmation'}));
 await waitFor(()=>expect(mocks.schedule).toHaveBeenCalledWith('visit',{professionalId:'doctor2',scheduledAt:'2026-10-14T10:00:00.000Z',version:4,reason:'Nouvelle affectation'}));
});
