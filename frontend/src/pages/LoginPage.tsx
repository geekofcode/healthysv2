import {useLocation} from 'react-router-dom';

import {useAuth} from '../auth/AuthContext';
import {useTranslation} from 'react-i18next';

export function LoginPage() {
  const auth = useAuth();
  const location = useLocation();
  const from = (location.state as {from?: string} | null)?.from ?? '/';
  const {t} = useTranslation();

  if (!auth.initialized) {
    return <main className="centered">{t('auth.keycloakInitializing')}</main>;
  }

  return (
    <main className="login-page">
      <section className="login-card">
        <p className="eyebrow">{t('auth.securePortal')}</p>
        <h1>{t('auth.welcome')}</h1>
        <p>{t('auth.intro')}</p>
        <button
          type="button"
          onClick={() => void auth.login(
            `${window.location.origin}${from}`,
          )}
        >
          {t('auth.login')}
        </button>
      </section>
    </main>
  );
}
