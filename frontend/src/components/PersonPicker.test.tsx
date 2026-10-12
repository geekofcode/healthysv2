// @vitest-environment jsdom
import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
vi.mock('react-i18next',()=>({useTranslation:()=>({i18n:{language:'fr'},t:(key:string)=>key})}));
vi.mock('../auth/AuthContext',()=>({useAuth:()=>({hasAnyRole:()=>false})}));
vi.mock('../api/client',()=>({ApiError:class extends Error{}}));
vi.mock('../api/persons',()=>({searchPersons:vi.fn(),createPerson:vi.fn()}));
import {createPerson,searchPersons,type Person} from '../api/persons';
import {PersonPicker} from './PersonPicker';
afterEach(()=>{cleanup();vi.clearAllMocks()});
it('lets hospital staff create an identified person without enumerating all identities or entering an UUID',async()=>{const onChange=vi.fn();vi.mocked(createPerson).mockResolvedValue({id:'generated-person',firstName:'Anne',lastName:'Martin',personNumber:'PER-GENERATED'} as Person);render(<QueryClientProvider client={new QueryClient()}><PersonPicker value="" onChange={onChange}/></QueryClientProvider>);expect(searchPersons).not.toHaveBeenCalled();fireEvent.click(screen.getByRole('button',{name:'Créer une nouvelle personne'}));fireEvent.change(screen.getByLabelText('Prénom'),{target:{value:'Anne'}});fireEvent.change(screen.getByLabelText('Nom'),{target:{value:'Martin'}});fireEvent.click(screen.getByRole('button',{name:'common.create'}));await waitFor(()=>expect(onChange).toHaveBeenCalledWith('generated-person'));expect(createPerson).toHaveBeenCalledWith({firstName:'Anne',lastName:'Martin',birthDate:undefined,gender:undefined},expect.anything());expect(screen.getByRole('status').textContent).toContain('PER-GENERATED');expect(searchPersons).not.toHaveBeenCalled()});
