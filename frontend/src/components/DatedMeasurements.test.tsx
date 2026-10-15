// @vitest-environment jsdom
import {useState} from 'react';
import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
vi.mock('../api/client',()=>({ApiError:class extends Error{}}));
vi.mock('../api/patientHealth',()=>({listReceptionHealthParameters:vi.fn().mockResolvedValue([{id:'parameter',label:'Douleur',valueType:'NUMERIC',active:true}]),listClinicalHealthParameters:vi.fn()}));
vi.mock('react-i18next',async()=>{const {default:messages}=await import('../translations/careFlow.json');return {useTranslation:()=>({t:(key:string)=>key.startsWith('careFlow.')?key.slice(9).split('.').reduce<any>((value,part)=>value?.[part],messages.fr)??key:key})};});
import {DatedMeasurements,measurementsAsInstants} from './DatedMeasurements';
import {listReceptionHealthParameters} from '../api/patientHealth';
function Form(){const [value,setValue]=useState<any[]>([]);return <><DatedMeasurements visitId="visit" value={value} onChange={setValue}/><output data-testid="values">{JSON.stringify(value)}</output></>;}
afterEach(()=>{cleanup();vi.clearAllMocks()});
it('loads the pre-cash catalog for the actual visit and records arbitrary dated parameters',async()=>{render(<QueryClientProvider client={new QueryClient()}><Form/></QueryClientProvider>);fireEvent.click(screen.getByRole('button',{name:'Ajouter une mesure'}));await screen.findByRole('option',{name:'Douleur'});expect(listReceptionHealthParameters).toHaveBeenCalledWith('visit');fireEvent.change(screen.getByLabelText('Paramètre'),{target:{value:'parameter'}});fireEvent.change(screen.getByLabelText('Valeur'),{target:{value:'4'}});fireEvent.change(screen.getByLabelText('Date et heure de mesure'),{target:{value:'2026-10-15T09:30'}});expect(JSON.parse(screen.getByTestId('values').textContent!)).toEqual([{parameterId:'parameter',value:'4',measuredAt:'2026-10-15T09:30'}]);expect(measurementsAsInstants([{parameterId:'parameter',value:'4',measuredAt:'2026-10-15T09:30'}])).toEqual([{parameterId:'parameter',value:'4',measuredAt:new Date('2026-10-15T09:30').toISOString()}]);});
