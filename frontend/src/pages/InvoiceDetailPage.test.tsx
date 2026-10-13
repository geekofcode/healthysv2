// @vitest-environment jsdom
import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {MemoryRouter,Route,Routes} from 'react-router-dom';
const mocks=vi.hoisted(()=>({payment:vi.fn().mockResolvedValue({}),roles:['CASHIER']}));
vi.mock('../api/client',()=>({ApiError:class extends Error{}}));
vi.mock('../auth/AuthContext',()=>({useAuth:()=>({hasAnyRole:(...roles:string[])=>roles.some(role=>mocks.roles.includes(role))})}));
vi.mock('react-i18next',()=>({useTranslation:()=>({t:(key:string)=>key,i18n:{language:'fr'}})}));
vi.mock('../api/billing',()=>({billingKeys:{invoice:(id:string)=>['invoice',id]},getInvoice:vi.fn().mockResolvedValue({id:'invoice-1',invoiceNumber:'INV-1',patientId:'p',organizationId:'o',careEpisodeId:'episode',currency:'CAD',totalAmount:25,amountPaid:0,balance:25,status:'ISSUED',items:[],payments:[]}),recordPayment:mocks.payment,issueInvoice:vi.fn(),cancelInvoice:vi.fn()}));
vi.mock('../api/careBilling',()=>({recordCareCash:mocks.payment,listCashReceipts:vi.fn().mockResolvedValue([]),refundCareCash:vi.fn()}));
vi.mock('../api/cutover',()=>({getCareCutover:vi.fn().mockResolvedValue({mode:'CARE_ONLY',migrationValidated:true,journeyValidated:true,restorationValidated:true})}));
import {getInvoice} from '../api/billing';
import {getCareCutover} from '../api/cutover';
import {InvoiceDetailPage} from './InvoiceDetailPage';
afterEach(()=>{cleanup();mocks.payment.mockClear();mocks.roles=['CASHIER']});
it('offers only cash collection and submits no electronic provider references',async()=>{
 render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><MemoryRouter initialEntries={['/billing/invoices/invoice-1']}><Routes><Route path="/billing/invoices/:id" element={<InvoiceDetailPage/>}/></Routes></MemoryRouter></QueryClientProvider>);
 await screen.findByText('INV-1');
 expect(screen.queryByRole('combobox')).toBeNull();
 expect(screen.queryByLabelText('billing.provider')).toBeNull();
 expect(screen.queryByLabelText('billing.externalReference')).toBeNull();
 expect(screen.getByText('billing.paymentMethod: billing.methods.CASH')).toBeTruthy();
 fireEvent.change(screen.getByLabelText('billing.amount'),{target:{value:'25'}});
 fireEvent.click(screen.getByRole('button',{name:'billing.confirmPayment'}));
 await waitFor(()=>expect(mocks.payment).toHaveBeenCalledWith('invoice-1',{amount:25,currency:'CAD'},expect.any(String)));
});

it('hides legacy issue/cancel after CARE_ONLY without hiding care cash collection',async()=>{mocks.roles=['ACCOUNTANT'];vi.mocked(getInvoice).mockResolvedValueOnce({id:'invoice-1',invoiceNumber:'OLD-1',issuedAt:'2026-10-13T10:00:00Z',subtotal:25,taxAmount:0,patientId:'p',organizationId:'o',currency:'CAD',totalAmount:25,amountPaid:0,balance:25,status:'DRAFT',items:[],payments:[]} as Awaited<ReturnType<typeof getInvoice>>);render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><MemoryRouter initialEntries={['/billing/invoices/invoice-1']}><Routes><Route path="/billing/invoices/:id" element={<InvoiceDetailPage/>}/></Routes></MemoryRouter></QueryClientProvider>);await screen.findByText('OLD-1');await waitFor(()=>expect(getCareCutover).toHaveBeenCalled());expect(screen.queryByRole('button',{name:'billing.issue'})).toBeNull();expect(screen.queryByRole('button',{name:'billing.cancelInvoice'})).toBeNull()});
