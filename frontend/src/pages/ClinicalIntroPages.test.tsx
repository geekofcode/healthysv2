// @vitest-environment jsdom
import {afterEach,describe,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter} from 'react-router-dom';
vi.mock('../api/client',()=>({ApiError:class extends Error{}}));
vi.mock('react-i18next',()=>({useTranslation:()=>({t:(key:string)=>key,i18n:{language:'fr'}})}));
vi.mock('../auth/AuthContext',()=>({useAuth:()=>({hasAnyRole:()=>true})}));
vi.mock('../api/appointments',()=>({appointmentKeys:{mine:['appointments','mine'],all:['appointments']},listAppointments:vi.fn().mockResolvedValue({content:[],page:{number:0,size:20,totalElements:0,totalPages:0,first:true,last:true}}),createAppointment:vi.fn(),cancelAppointment:vi.fn(),changeAppointmentStatus:vi.fn(),rescheduleAppointment:vi.fn()}));
vi.mock('../api/teleconsultations',()=>({teleconsultationKeys:{all:['video-sessions']},listVideoSessions:vi.fn().mockResolvedValue([]),createVideoSession:vi.fn()}));
import {AgendaPage} from './AgendaPage';
import {TeleconsultationsPage} from './TeleconsultationsPage';
afterEach(cleanup);
function show(page:React.ReactNode){render(<MemoryRouter><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}>{page}</QueryClientProvider></MemoryRouter>);}
describe('clinical list entry pages',()=>{it('opens the agenda on a table with the creation form hidden',async()=>{show(<AgendaPage/>);await screen.findByRole('table');expect(screen.queryByLabelText('agenda.patientId')).toBeNull();fireEvent.click(screen.getByRole('button',{name:'agenda.create'}));expect(screen.getByLabelText('agenda.patientId')).toBeTruthy();});it('opens video sessions on a table and only shows creation after clicking New',async()=>{show(<TeleconsultationsPage/>);await screen.findByRole('table');expect(screen.queryByLabelText('teleconsultation.sourceId')).toBeNull();fireEvent.click(screen.getByRole('button',{name:'teleconsultation.create'}));expect(screen.getByLabelText('teleconsultation.sourceId')).toBeTruthy();});});
