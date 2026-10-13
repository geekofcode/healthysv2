// @vitest-environment jsdom
import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,render,screen} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter} from 'react-router-dom';
const mocks=vi.hoisted(()=>({get:vi.fn(),admin:false}));
vi.mock('../auth/AuthContext',()=>({useAuth:()=>({hasAnyRole:()=>mocks.admin})}));
vi.mock('../auth/keycloak',()=>({keycloak:{tokenParsed:{sub:'u'}}}));
vi.mock('../auth/organizationContext',()=>({selectedOrganization:()=> 'o'}));
vi.mock('../api/licensing',()=>({licensingKeys:{organization:(id:string)=>['licensing',id]},getEntitlements:mocks.get}));
vi.mock('../api/client',()=>({ApiError:class extends Error{}}));
vi.mock('react-i18next',()=>({useTranslation:()=>({i18n:{language:'fr'}})}));
import {LicenseBoundary} from './LicenseBoundary';
function mount(path:string){render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><MemoryRouter initialEntries={[path]}><LicenseBoundary><form><p>Operational form</p><input aria-label="Notes"/><button>Save</button></form></LicenseBoundary></MemoryRouter></QueryClientProvider>)}
afterEach(()=>{cleanup();vi.clearAllMocks();mocks.admin=false});
it('preserves read screens and disables mutation forms for inactive license',async()=>{mocks.get.mockResolvedValue({active:false});mount('/consultations/new');await screen.findByRole('alert');expect(screen.getByText('Operational form')).toBeTruthy();expect(screen.getByLabelText('Notes').closest('fieldset')?.disabled).toBe(true);expect(screen.getByRole('link',{name:'Consulter la licence'}).getAttribute('href')).toBe('/organizations/o/license');});
it('leaves subscription repair accessible without querying entitlements',()=>{mount('/organizations/o/license');expect(screen.getByText('Operational form')).toBeTruthy();expect(mocks.get).not.toHaveBeenCalled()});
