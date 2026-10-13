// @vitest-environment jsdom
import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,render,screen} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter} from 'react-router-dom';
vi.mock('react-i18next',()=>({useTranslation:()=>({i18n:{language:'fr'}})}));
vi.mock('../api/client',()=>({apiRequest:vi.fn(),ApiError:class extends Error{}}));
import {apiRequest} from '../api/client';
import {PatientCareAgendaPage} from './PatientCareAgendaPage';
afterEach(()=>{cleanup();vi.resetAllMocks()});
it('reads patient visits without clinical workspace links or patient write controls',async()=>{vi.mocked(apiRequest).mockResolvedValue([{id:'visit',episodeId:'episode',organizationId:'org',organizationName:'Clinique',doctorName:'Dr Anne',scheduledAt:'2026-10-13T14:00:00Z',status:'SCHEDULED',reason:'Contrôle',followUp:true}]);render(<MemoryRouter><QueryClientProvider client={new QueryClient()}><PatientCareAgendaPage/></QueryClientProvider></MemoryRouter>);expect(await screen.findByText('Clinique')).toBeTruthy();expect(screen.getByText('Contrôle')).toBeTruthy();expect(apiRequest).toHaveBeenCalledWith('/care/visits/me');expect(screen.queryAllByRole('button')).toHaveLength(0);expect(screen.getAllByRole('link')).toHaveLength(1);expect(screen.getByRole('link').getAttribute('href')).toBe('/agenda/legacy')});
