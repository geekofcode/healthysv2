// @vitest-environment jsdom
import {afterEach,expect,it,vi} from 'vitest';
import {cleanup,fireEvent,render,screen,waitFor} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {LanguageSwitch} from './LanguageSwitch';
import {getPreferences,updatePreferences} from '../api/preferences';
const language=vi.hoisted(()=>({changeLanguage:vi.fn()}));
vi.mock('react-i18next',()=>({useTranslation:()=>({i18n:{language:'fr',changeLanguage:language.changeLanguage}})}));
vi.mock('../api/preferences',()=>({getPreferences:vi.fn(),updatePreferences:vi.fn()}));
afterEach(()=>{cleanup();vi.clearAllMocks();vi.restoreAllMocks();vi.unstubAllGlobals();});
it('uses system language by default and reacts to system language changes',()=>{
 vi.spyOn(navigator,'languages','get').mockReturnValue(['fr-CA']);
 render(<QueryClientProvider client={new QueryClient()}><LanguageSwitch hidden/></QueryClientProvider>);
 expect(language.changeLanguage).toHaveBeenCalledWith('fr');expect(screen.queryByRole('combobox')).toBeNull();
 vi.spyOn(navigator,'languages','get').mockReturnValue(['en-CA']);fireEvent(window,new Event('languagechange'));expect(language.changeLanguage).toHaveBeenCalledWith('en');
});
it('saves language without overwriting the account theme or avatar',async()=>{
 const preferences={theme:'DARK' as const,avatarUrl:'https://example.com/avatar.png',language:'SYSTEM' as const};
 vi.mocked(getPreferences).mockResolvedValue(preferences);vi.mocked(updatePreferences).mockResolvedValue({...preferences,language:'EN'});
 render(<QueryClientProvider client={new QueryClient()}><LanguageSwitch authenticated/></QueryClientProvider>);
 await waitFor(()=>expect((screen.getByRole('combobox') as HTMLSelectElement).disabled).toBe(false));
 fireEvent.change(screen.getByRole('combobox'),{target:{value:'EN'}});
 await waitFor(()=>expect(updatePreferences).toHaveBeenCalledWith({language:'EN'}));
});
