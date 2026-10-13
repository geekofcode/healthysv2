// @vitest-environment jsdom
import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,render,screen} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
vi.mock('react-i18next',()=>({useTranslation:()=>({i18n:{language:'fr'}})}));
vi.mock('../api/client',()=>({ApiError:class extends Error{}}));
vi.mock('../api/careHistory',()=>({getCarePatientHistory:vi.fn()}));
vi.mock('../api/licensing',()=>({getEntitlements:vi.fn().mockResolvedValue({active:true,aiEnabled:false})}));
import {getCarePatientHistory} from '../api/careHistory';
import {getEntitlements} from '../api/licensing';
import {CarePatientHistory} from './CarePatientHistory';
afterEach(()=>{cleanup();vi.clearAllMocks()});
function show(){render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><CarePatientHistory patientId="patient" currentVisitId="current"/></QueryClientProvider>)}
it('shows all validated fields and origins without AI entitlement or foreign visit editing',async()=>{vi.mocked(getCarePatientHistory).mockResolvedValue([{visitId:'foreign',episodeId:'e',organizationId:'other',organizationName:'Autre clinique',doctorName:'Dr Martin',revision:2,validatedAt:'2026-10-12T14:00:00Z',reason:'Motif antérieur',observations:'Observations antérieures',diagnosis:'Diagnostic antérieur',treatment:'Traitement antérieur',followUp:'Suivi antérieur',historical:true},{visitId:'current',episodeId:'e2',organizationId:'here',organizationName:'Actuelle',doctorName:'Dr Anne',revision:1,validatedAt:'2026-10-13T14:00:00Z',reason:'À exclure',historical:false}]);show();expect(await screen.findByText('Autre clinique · Dr Martin')).toBeTruthy();for(const text of ['Motif antérieur','Observations antérieures','Diagnostic antérieur','Traitement antérieur','Suivi antérieur'])expect(screen.getByText(text)).toBeTruthy();expect(screen.queryByText('À exclure')).toBeNull();expect(screen.queryAllByRole('link')).toHaveLength(0);expect(screen.queryAllByRole('button')).toHaveLength(0);expect(getEntitlements).not.toHaveBeenCalled();expect(getCarePatientHistory).toHaveBeenCalledWith('patient')});
it('isolates history access failures from the manual consultation',async()=>{vi.mocked(getCarePatientHistory).mockRejectedValue(new Error('Historique non autorisé'));render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><button>Valider manuellement</button><CarePatientHistory patientId="patient"/></QueryClientProvider>);expect(await screen.findByText('Historique non autorisé')).toBeTruthy();expect((screen.getByRole('button',{name:'Valider manuellement'}) as HTMLButtonElement).disabled).toBe(false)});
