// @vitest-environment jsdom
import {afterEach,describe,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
vi.mock('../api/client',()=>({ApiError:class extends Error{}}));
vi.mock('react-i18next',()=>({useTranslation:()=>({i18n:{language:'fr'}})}));
vi.mock('../api/dataDeletion',()=>({listMyDataDeletionRequests:vi.fn(),requestMyDataDeletion:vi.fn()}));
import {listMyDataDeletionRequests,requestMyDataDeletion,type DataDeletionRequest} from '../api/dataDeletion';
import {DataDeletionPanel} from './DataDeletionPanel';
afterEach(()=>{cleanup();vi.resetAllMocks()});
const request:DataDeletionRequest={id:'request',status:'QUEUED',createdAt:'2026-10-13T10:00:00Z',removedCount:0,anonymizedCount:0,retainedCount:4,notes:'Dossier partagé conservé'};
function show(){render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}})}><DataDeletionPanel/></QueryClientProvider>)}
describe('self-data deletion',()=>{
 it('requires explicit confirmation and preserves the retry key after network failure',async()=>{vi.mocked(listMyDataDeletionRequests).mockResolvedValue([]);vi.mocked(requestMyDataDeletion).mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce(request);show();const input=screen.getByLabelText('Tapez SUPPRIMER pour confirmer');const button=screen.getByRole('button',{name:'Demander la suppression'});expect((button as HTMLButtonElement).disabled).toBe(true);fireEvent.change(input,{target:{value:'SUPPRIMER'}});await waitFor(()=>expect((button as HTMLButtonElement).disabled).toBe(false));fireEvent.click(button);await waitFor(()=>expect(requestMyDataDeletion).toHaveBeenCalledTimes(1));await waitFor(()=>expect((button as HTMLButtonElement).disabled).toBe(false));fireEvent.click(button);await waitFor(()=>expect(requestMyDataDeletion).toHaveBeenCalledTimes(2));expect(vi.mocked(requestMyDataDeletion).mock.calls[0]?.[0]).toBe(vi.mocked(requestMyDataDeletion).mock.calls[1]?.[0]);expect(await screen.findByText('Demande enregistrée. Consultez son avancement ci-dessous.')).toBeTruthy();expect(screen.getByText(/ne supprime pas automatiquement/)).toBeTruthy()});
 it('shows retained data and prevents a second request during ongoing processing',async()=>{vi.mocked(listMyDataDeletionRequests).mockResolvedValue([request]);show();expect(await screen.findByText('Dossier partagé conservé')).toBeTruthy();expect(screen.getByText(/Conservés : 4/)).toBeTruthy();expect((screen.getByLabelText('Tapez SUPPRIMER pour confirmer') as HTMLInputElement).disabled).toBe(true)});
 it('fails closed when request history cannot be read',async()=>{vi.mocked(listMyDataDeletionRequests).mockRejectedValue(new Error('unavailable'));show();fireEvent.change(screen.getByLabelText('Tapez SUPPRIMER pour confirmer'),{target:{value:'SUPPRIMER'}});await waitFor(()=>expect(screen.getByText('unavailable')).toBeTruthy());expect((screen.getByRole('button',{name:'Demander la suppression'}) as HTMLButtonElement).disabled).toBe(true)});
});
