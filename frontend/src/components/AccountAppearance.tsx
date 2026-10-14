import {useAuth} from '../auth/AuthContext';
import {ThemeSwitch} from './ThemeSwitch';
import {LanguageSwitch} from './LanguageSwitch';
/** Applies preferences everywhere without exposing controls outside account settings. */
export function AccountAppearance(){const {authenticated}=useAuth();return <><ThemeSwitch authenticated={authenticated} hidden/><LanguageSwitch authenticated={authenticated} hidden/></>;}
