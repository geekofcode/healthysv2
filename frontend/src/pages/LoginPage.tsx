import {LanguageSwitch} from '../components/LanguageSwitch';
import {Navigate,useLocation} from 'react-router-dom';
import {useAuth} from '../auth/AuthContext';
import {useTranslation} from 'react-i18next';
import {ThemeSwitch} from '../components/ThemeSwitch';

export function LoginPage() {
  const auth = useAuth();
  const location = useLocation();
  const from = (location.state as {from?: string} | null)?.from ?? '/';
  const {t,i18n} = useTranslation();
  const french = i18n?.language?.startsWith('fr');
  if(auth.initialized&&auth.authenticated)return <Navigate to="/profiles/select" replace/>;
  return <main className="login-page">
    <header className="public-header"><a className="brand" href="/">HEALTH’YS</a><LanguageSwitch /><ThemeSwitch /></header>
    <div className="welcome-layout">
      <section className="welcome-story">
        <p className="eyebrow">{t('auth.securePortal')}</p>
        <h1>{t('accountFlow.loginPage1')}</h1>
        <p className="welcome-intro">{t('accountFlow.loginPage2')}</p>
        <div className="welcome-benefits">
          <div><span aria-hidden="true">01</span><div><h2>{t('accountFlow.loginPage3')}</h2><p>{t('accountFlow.loginPage4')}</p></div></div>
          <div><span aria-hidden="true">02</span><div><h2>{t('accountFlow.loginPage5')}</h2><p>{t('accountFlow.loginPage6')}</p></div></div>
        </div>
      </section>
      <section className="login-card" aria-labelledby="login-title">
        <div className="login-mark" aria-hidden="true">H</div><h2 id="login-title">{t('accountFlow.loginPage7')}</h2>
        <p>{t('accountFlow.loginPage8')}</p>
        <button type="button" disabled={!auth.initialized} onClick={() => void auth.login(`${window.location.origin}${from}`)}>{auth.initialized ? t('auth.login') : t('auth.keycloakInitializing')}</button>
        <div className="auth-divider"><span>{t('accountFlow.loginPage9')}</span></div>
        <button className="button-secondary" disabled={!auth.initialized} type="button" onClick={() => void auth.register(`${window.location.origin}/registration/complete`)}>{t('registration.createAccount')}</button>

        <p className="auth-security-note">{t('accountFlow.loginPage10')}</p>
      </section>
    </div>
    <footer className="public-footer">HEALTH’YS · {t('accountFlow.loginPage11')}</footer>
  </main>;
}
