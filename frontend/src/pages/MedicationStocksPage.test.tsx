// @vitest-environment jsdom
import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter} from 'react-router-dom';
const fixtures=vi.hoisted(()=>({role:'PLATFORM_ADMIN',stocks:Array.from({length:21},(_,i)=>({id:String(i),organizationId:'org',medicationCatalogId:'med',medicationCode:'MED',medicationName:'Medication',batchNumber:`BATCH-${i}`,quantity:5,expired:false}))}));
vi.mock('../api/client',()=>({ApiError:class extends Error{}}));
vi.mock('../auth/AuthContext',()=>({useAuth:()=>({hasAnyRole:(...roles:string[])=>roles.includes(fixtures.role)})}));
vi.mock('react-i18next',()=>({useTranslation:()=>({t:(key:string)=>key,i18n:{language:'fr'}})}));
vi.mock('../components/ClinicalReferenceSelect',()=>({ClinicalReferenceSelect:({label,value,onChange}:{label:string;value:string;onChange:(value:string)=>void})=><label>{label}<select value={value} onChange={e=>onChange(e.target.value)}><option value=""/><option value="org">Organization</option></select></label>}));
vi.mock('../api/pharmacy',()=>({listMedicationCatalog:vi.fn().mockResolvedValue([{id:'med',code:'MED',name:'Medication'}]),listMedicationStocks:vi.fn().mockResolvedValue(fixtures.stocks),replenishMedicationStock:vi.fn(),validStockInput:vi.fn(),pharmacyKeys:{catalog:()=>['catalog'],stocks:()=>['stocks']}}));
import {MedicationStocksPage} from './MedicationStocksPage';
afterEach(()=>{cleanup();fixtures.role='PLATFORM_ADMIN'});
function show(){render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><MemoryRouter><MedicationStocksPage/></MemoryRouter></QueryClientProvider>)}
it('starts with a paginated stock list and opens creation only on request',async()=>{show();await screen.findByText('BATCH-0');expect(screen.queryByLabelText('pharmacy.batchNumber')).toBeNull();expect(screen.queryByText('BATCH-20')).toBeNull();fireEvent.click(screen.getByRole('button',{name:'Suivant'}));expect(screen.getByText('BATCH-20')).toBeTruthy();fireEvent.click(screen.getByRole('button',{name:'Nouveau stock'}));expect(screen.getByLabelText('pharmacy.batchNumber')).toBeTruthy();fireEvent.click(screen.getByRole('button',{name:'common.cancel'}));expect(screen.queryByLabelText('pharmacy.batchNumber')).toBeNull()});
it('opens replenishment for the chosen lot',async()=>{show();await screen.findByText('BATCH-0');fireEvent.click(screen.getAllByRole('button',{name:'pharmacy.replenish'})[0]!);expect((screen.getByLabelText('pharmacy.batchNumber') as HTMLInputElement).value).toBe('BATCH-0')});
it('does not offer stock creation to a patient',async()=>{fixtures.role='PATIENT';show();await screen.findByText('BATCH-0');expect(screen.queryByRole('button',{name:'Nouveau stock'})).toBeNull();expect(screen.queryByRole('button',{name:'pharmacy.replenish'})).toBeNull()});
