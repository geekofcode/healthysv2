vi.mock('../api/client',()=>({ApiError:class extends Error{}}));
// @vitest-environment jsdom
import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter,Route,Routes} from 'react-router-dom';
import {HospitalPermissionContext} from '../auth/HospitalPermissions';
const mock=vi.hoisted(()=>({save:vi.fn().mockResolvedValue({}),remove:vi.fn()}));
vi.mock('../auth/keycloak',()=>({keycloak:{tokenParsed:{sub:'me'}}}));
vi.mock('react-i18next',()=>({useTranslation:()=>({i18n:{language:'fr'}})}));
vi.mock('../api/hospitalAccess',()=>({accessKeys:{all:(org:string)=>['hospital-access',org]},getPermissionCatalog:vi.fn().mockResolvedValue([{resource:'invoices',label:'Factures',actions:['CREATE','READ','UPDATE','DELETE']},{resource:'cashreports',label:'Rapports de caisse',actions:['READ']}]),listHospitalRoles:vi.fn().mockResolvedValue([{id:'r',name:'Gestionnaire',organizationId:'o',version:2,permissions:[{resource:'cashreports',actions:['READ']}]}]),saveHospitalRole:mock.save,deleteHospitalRole:mock.remove}));
import {HospitalRolesPage} from './HospitalRolesPage';
afterEach(()=>{cleanup();vi.clearAllMocks()});
function mount(administrator=true){render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><HospitalPermissionContext.Provider value={{organizationId:'o',administrator,permissions:administrator?[]:[{resource:'roles',actions:['READ']}]}}><MemoryRouter initialEntries={['/organizations/o/roles']}><Routes><Route path="/organizations/:id/roles" element={<HospitalRolesPage/>}/></Routes></MemoryRouter></HospitalPermissionContext.Provider></QueryClientProvider>);}
it('creates a role with only the selected invoice CRUD permissions',async()=>{mount();await screen.findByLabelText('Factures READ');fireEvent.change(screen.getByLabelText('Nom du rôle'),{target:{value:'Caisse'}});for(const action of ['CREATE','READ','UPDATE','DELETE'])fireEvent.click(screen.getByLabelText(`Factures ${action}`));fireEvent.click(screen.getByRole('button',{name:'Enregistrer le rôle'}));await waitFor(()=>expect(mock.save).toHaveBeenCalledWith('o',{name:'Caisse',description:'',permissions:[{resource:'invoices',actions:['CREATE','READ','UPDATE','DELETE']}]},undefined));expect((screen.getByLabelText('Rapports de caisse CREATE') as HTMLInputElement).disabled).toBe(true);});
it('preserves optimistic version when editing an access role',async()=>{mount();await screen.findByText('Gestionnaire');fireEvent.click(screen.getByRole('button',{name:'Modifier'}));fireEvent.change(screen.getByLabelText('Nom du rôle'),{target:{value:'Gestionnaire caisse'}});fireEvent.click(screen.getByRole('button',{name:'Enregistrer le rôle'}));await waitFor(()=>expect(mock.save).toHaveBeenCalledWith('o',expect.objectContaining({name:'Gestionnaire caisse',version:2}), 'r'));});
it('hides role mutations for a read-only role manager',async()=>{mount(false);await screen.findByText('Gestionnaire');expect(screen.queryByRole('button',{name:'Modifier'})).toBeNull();expect(screen.queryByRole('button',{name:'Supprimer'})).toBeNull();expect(screen.queryByRole('button',{name:'Enregistrer le rôle'})).toBeNull();});
