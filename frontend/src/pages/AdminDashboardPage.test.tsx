// @vitest-environment jsdom
import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,render,screen} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter} from 'react-router-dom';
const mock=vi.hoisted(()=>({request:vi.fn().mockResolvedValue({organizations:2,patients:40,employees:12,episodes:100,visits:125,completedVisits:90,documents:32,invoices:98,cashOperations:110})}));
vi.mock('../api/client',()=>({apiRequest:mock.request,ApiError:class extends Error{}}));
vi.mock('react-i18next',()=>({useTranslation:()=>({t:(key:string)=>key,i18n:{language:'fr'}})}));
import {AdminDashboardPage} from './AdminDashboardPage';
import {OrganizationStatistics} from '../components/OrganizationStatistics';
afterEach(()=>{cleanup();vi.clearAllMocks()});
it('loads only aggregate statistics and links to platform administration',async()=>{render(<QueryClientProvider client={new QueryClient()}><MemoryRouter><AdminDashboardPage/></MemoryRouter></QueryClientProvider>);await screen.findByText('125');expect(mock.request).toHaveBeenCalledWith('/admin/statistics');expect(mock.request).toHaveBeenCalledTimes(1);for(const path of ['/patients','/professionals','/billing','/admin/audit-security'])expect(document.querySelector(`a[href="${path}"]`)).toBeNull();expect(screen.getByRole('link',{name:'Comptes utilisateurs'}).getAttribute('href')).toBe('/admin/accounts');});
it('uses aggregate statistics scoped to the organization profile',async()=>{render(<QueryClientProvider client={new QueryClient()}><OrganizationStatistics organizationId="org-a"/></QueryClientProvider>);await screen.findByText('125');expect(mock.request).toHaveBeenCalledWith('/admin/organizations/org-a/statistics');});
