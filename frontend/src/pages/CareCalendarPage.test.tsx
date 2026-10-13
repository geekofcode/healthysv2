// @vitest-environment jsdom
import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter} from 'react-router-dom';
const mock=vi.hoisted(()=>({order:vi.fn().mockResolvedValue({}),next:vi.fn().mockResolvedValue({}),role:'DOCTOR'}));
vi.mock('../api/client',()=>({ApiError:class extends Error{}}));
vi.mock('../auth/AuthContext',()=>({useAuth:()=>({hasAnyRole:(...roles:string[])=>roles.includes(mock.role)})}));
vi.mock('react-i18next',()=>({useTranslation:()=>({i18n:{language:'fr'}})}));
vi.mock('../api/careEpisodes',()=>({getReceptionOptions:vi.fn().mockResolvedValue({doctors:[]})}));
vi.mock('../api/careCalendar',()=>({getCareCalendar:vi.fn().mockResolvedValue({professionalId:'d',date:'2026-10-13',version:7,visits:[{visitId:'v1',episodeId:'e1',patientFirstName:'Alice',patientLastName:'One',scheduledAt:'2026-10-13T10:00:00Z',status:'SCHEDULED',presence:'PRESENT'},{visitId:'v2',episodeId:'e2',patientFirstName:'Bob',patientLastName:'Two',scheduledAt:'2026-10-13T11:00:00Z',status:'SCHEDULED',presence:'WAITING'}]}),setCarePresence:vi.fn(),callCareNext:mock.next,reorderCareQueue:mock.order}));
import {CareCalendarPage} from './CareCalendarPage';
afterEach(()=>{cleanup();vi.clearAllMocks();});
it('doctor can reorder and call next with the loaded queue version',async()=>{
 render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><MemoryRouter><CareCalendarPage/></MemoryRouter></QueryClientProvider>);
 await screen.findByText('Alice One');
 expect(screen.queryByRole('button',{name:'Marquer présent'})).toBeNull();
 fireEvent.click(screen.getAllByRole('button',{name:'Descendre'})[0]!);
 await waitFor(()=>expect(mock.order).toHaveBeenCalledWith(expect.objectContaining({professionalId:'d',visitIds:['v2','v1'],expectedVersion:7}),expect.anything()));
 fireEvent.click(screen.getByRole('button',{name:'Appeler le prochain patient'}));
 await waitFor(()=>expect(mock.next).toHaveBeenCalledWith(expect.objectContaining({professionalId:'d',expectedVersion:7}),expect.anything()));
});
