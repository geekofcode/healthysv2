// @vitest-environment jsdom
import {afterEach,describe,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {ThemeSwitch} from './ThemeSwitch';
import {getPreferences,updatePreferences} from '../api/preferences';
vi.mock('../api/preferences',()=>({getPreferences:vi.fn(),updatePreferences:vi.fn()}));
vi.mock('react-i18next',()=>({useTranslation:()=>({i18n:{language:'fr'}})}));
afterEach(()=>{cleanup();localStorage.clear();delete document.documentElement.dataset.theme;});
describe('display theme',()=>{
 it('exposes a labelled preference and persists an explicit dark choice across mounts',()=>{
  const client = new QueryClient();
  const view=render(<QueryClientProvider client={client}><ThemeSwitch /></QueryClientProvider>);
  fireEvent.change(screen.getByRole('combobox',{name:'Thème d’affichage'}),{target:{value:'dark'}});
  expect(document.documentElement.dataset.theme).toBe('dark');
  expect(localStorage.getItem('healthys.theme')).toBe('dark');
  view.unmount();
  render(<QueryClientProvider client={client}><ThemeSwitch /></QueryClientProvider>);
  expect((screen.getByRole('combobox') as HTMLSelectElement).value).toBe('dark');
 });
 it('loads the account preference and saves the theme without replacing the profile photo',async()=>{
  vi.mocked(getPreferences).mockResolvedValue({theme:'LIGHT',avatarUrl:'https://example.com/avatar.png'});
  vi.mocked(updatePreferences).mockResolvedValue({theme:'DARK',avatarUrl:'https://example.com/avatar.png'});
  render(<QueryClientProvider client={new QueryClient({defaultOptions:{queries:{retry:false}}})}><ThemeSwitch authenticated /></QueryClientProvider>);
  await waitFor(()=>expect((screen.getByRole('combobox') as HTMLSelectElement).disabled).toBe(false));
  fireEvent.change(screen.getByRole('combobox'),{target:{value:'dark'}});
  await waitFor(()=>expect(updatePreferences).toHaveBeenCalledWith({theme:'DARK',avatarUrl:'https://example.com/avatar.png'}));
 });
});
