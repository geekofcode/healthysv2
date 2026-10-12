import {useLocation} from 'react-router-dom';
import {useAuth} from '../auth/AuthContext';
import {useTranslation} from 'react-i18next';
import {ThemeSwitch} from '../components/ThemeSwitch';

export function LoginPage() {
  const auth = useAuth();
  const location = useLocation();
  const from = (location.state as {from?: string} | null)?.from ?? '/';
  const {t,i18n} = useTranslation();
  const french = i18n?.language?.startsWith('fr');
  return <main className="login-page">
    <header className="public-header"><a className="brand" href="/">HEALTH’YS</a><ThemeSwitch /></header>
    <div className="welcome-layout">
      <section className="welcome-story">
        <p className="eyebrow">{t('auth.securePortal')}</p>
        <h1>{french ? 'Votre santé, un parcours plus simple.' : 'Your health, a simpler journey.'}</h1>
        <p className="welcome-intro">{french ? 'Un espace pour retrouver votre dossier, organiser vos rendez-vous et rester en contact avec les professionnels qui vous accompagnent.' : 'One place to find your health records, organize appointments and stay connected with your care team.'}</p>
        <div className="welcome-benefits">
          <div><span aria-hidden="true">01</span><div><h2>{french ? 'Vos informations réunies' : 'Your records together'}</h2><p>{french ? 'Consultations, résultats et prescriptions dans votre espace personnel.' : 'Consultations, results and prescriptions in your personal space.'}</p></div></div>
          <div><span aria-hidden="true">02</span><div><h2>{french ? 'Un suivi en toute confiance' : 'Care you can trust'}</h2><p>{french ? 'Vous gérez les accès à votre dossier et les échanges avec votre équipe de soins.' : 'Manage access to your records and conversations with your care team.'}</p></div></div>
        </div>
      </section>
      <section className="login-card" aria-labelledby="login-title">
        <div className="login-mark" aria-hidden="true">H</div><h2 id="login-title">{french ? 'Bienvenue dans votre espace' : 'Welcome to your space'}</h2>
        <p>{french ? 'Connectez-vous pour accéder à HEALTH’YS.' : 'Sign in to access HEALTH’YS.'}</p>
        <button type="button" disabled={!auth.initialized} onClick={() => void auth.login(`${window.location.origin}${from}`)}>{auth.initialized ? t('auth.login') : t('auth.keycloakInitializing')}</button>
        <div className="auth-divider"><span>{french ? 'Première visite ?' : 'First visit?'}</span></div>
        <button className="button-secondary" disabled={!auth.initialized} type="button" onClick={() => void auth.register(`${window.location.origin}/registration/complete`)}>{t('registration.createAccount')}</button>
        <button className="auth-professional-link" disabled={!auth.initialized} type="button" onClick={() => void auth.register(`${window.location.origin}${from.startsWith('/professional-invitation') ? from : '/professional-onboarding'}`)}>{t('onboarding.createProfessionalAccount')} <span aria-hidden="true">→</span></button>
        <p className="auth-security-note">{french ? 'Connexion sécurisée. Vos données de santé restent dans votre espace HEALTH’YS.' : 'Secure sign-in. Your health data stays in your HEALTH’YS space.'}</p>
      </section>
    </div>
    <footer className="public-footer">HEALTH’YS · {french ? 'Votre santé, à vos côtés.' : 'Your health, by your side.'}</footer>
  </main>;
}
