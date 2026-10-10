import {useQuery, useQueryClient} from '@tanstack/react-query';
import {getPreferences, updatePreferences} from '../api/preferences';
import {useEffect, useState} from 'react';
import {useTranslation} from 'react-i18next';

export type ThemePreference = 'light' | 'dark' | 'system';
const storageKey = 'healthys.theme';
function initialTheme(): ThemePreference {
  try {const saved = localStorage.getItem(storageKey); if (saved === 'light' || saved === 'dark') return saved;} catch { /* Storage can be disabled. */ }
  return 'system';
}
export function ThemeSwitch({authenticated = false}: {authenticated?:boolean}) {
  const {i18n} = useTranslation();
  const french = i18n?.language?.startsWith('fr');
  const queryClient = useQueryClient();
  const preferences = useQuery({queryKey:['person','preferences'],queryFn:getPreferences,enabled:authenticated});
  const [saveError,setSaveError] = useState(false);
  const [preference, setPreference] = useState<ThemePreference>(initialTheme);
  useEffect(() => {
    if (authenticated && preferences.data?.theme) setPreference(preferences.data.theme.toLowerCase() as ThemePreference);
  }, [authenticated,preferences.data?.theme]);
  const change = async (theme:ThemePreference) => {
    setPreference(theme);
    setSaveError(false);
    if (!authenticated) return;
    try {
      const saved = await updatePreferences({theme:theme.toUpperCase() as 'LIGHT'|'DARK'|'SYSTEM',avatarUrl:preferences.data?.avatarUrl ?? null});
      queryClient.setQueryData(['person','preferences'], saved);
    } catch {setSaveError(true);}
  };
  useEffect(() => {
    const media = window.matchMedia?.('(prefers-color-scheme: dark)');
    const apply = () => {document.documentElement.dataset.theme = preference === 'system' ? (media?.matches ? 'dark' : 'light') : preference;};
    apply();
    media?.addEventListener('change', apply);
    try {localStorage.setItem(storageKey, preference);} catch { /* Keep the in-memory preference. */ }
    return () => media?.removeEventListener('change', apply);
  }, [preference]);
  return <label className="theme-switch"><span className="sr-only">{french ? 'Thème d’affichage' : 'Display theme'}</span><select disabled={authenticated && !preferences.data?.theme} value={preference} onChange={event => void change(event.target.value as ThemePreference)} aria-label={french ? 'Thème d’affichage' : 'Display theme'}><option value="system">{french ? 'Thème système' : 'System theme'}</option><option value="light">{french ? 'Clair' : 'Light'}</option><option value="dark">{french ? 'Sombre' : 'Dark'}</option></select>{saveError && <span className="theme-save-error" role="status">{french ? 'Préférence non enregistrée' : 'Preference not saved'}</span>}</label>;
}
