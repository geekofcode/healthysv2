// @vitest-environment jsdom
import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,render,screen} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter} from 'react-router-dom';
vi.mock('../api/client',()=>({ApiError:class extends Error{}}));
vi.mock('react-i18next',()=>({useTranslation:()=>({i18n:{language:'fr'}})}));
vi.mock('../api/cutover',()=>({getCareCutover:vi.fn()}));
import {getCareCutover} from '../api/cutover';
import {LegacyCareBoundary} from './LegacyCareBoundary';
afterEach(()=>{cleanup();vi.resetAllMocks()});
function show(writeOnly=false){render(<MemoryRouter><QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><LegacyCareBoundary writeOnly={writeOnly}>{readOnly=><p>{readOnly?'history':'legacy write'}</p>}</LegacyCareBoundary></QueryClientProvider></MemoryRouter>)}
it('keeps legacy creation available before validated cutover',async()=>{vi.mocked(getCareCutover).mockResolvedValue({mode:'PILOT',migrationValidated:false,journeyValidated:true,restorationValidated:false});show(true);expect(await screen.findByText('legacy write')).toBeTruthy()});
it('replaces legacy creation after cutover and preserves read-only history',async()=>{vi.mocked(getCareCutover).mockResolvedValue({mode:'CARE_ONLY',migrationValidated:true,journeyValidated:true,restorationValidated:true});show(true);expect(await screen.findByRole('link',{name:'Ouvrir le calendrier'})).toBeTruthy();expect(screen.queryByText('legacy write')).toBeNull();cleanup();show();expect(await screen.findByText('history')).toBeTruthy()});
it('does not expose legacy writers if cutover status fails',async()=>{vi.mocked(getCareCutover).mockRejectedValue(new Error('unavailable'));show(true);expect(await screen.findByText('unavailable')).toBeTruthy();expect(screen.queryByText('legacy write')).toBeNull()});
