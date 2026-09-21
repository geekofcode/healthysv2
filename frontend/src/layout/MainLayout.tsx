import {NavLink, Outlet} from 'react-router-dom';

import {useAuth} from '../auth/AuthContext';
import {useTranslation} from 'react-i18next';

export function MainLayout() {
  const auth = useAuth();
  const {t} = useTranslation();

  return (
    <div className="app-shell">
      <header className="topbar">
        <NavLink className="brand" to="/">HEALTH'YS</NavLink>
        <nav aria-label={t('nav.main')}>
          <NavLink to="/">{t('nav.home')}</NavLink>
          <NavLink to="/me">{t('nav.profile')}</NavLink>
          <NavLink to="/organizations">{t('nav.organizations')}</NavLink>
          {auth.hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','HOSPITAL_AGENT','DOCTOR','NURSE','PHARMACIST','LAB_TECHNICIAN')&&<NavLink to="/professionals">{t('nav.professionals')}</NavLink>}
          {auth.hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','HOSPITAL_AGENT','DOCTOR','NURSE','PHARMACIST','LAB_TECHNICIAN')&&<NavLink to="/patients">{t('nav.patients')}</NavLink>}
          {auth.hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN','HOSPITAL_AGENT','DOCTOR','NURSE','PATIENT')&&<NavLink to="/agenda">{t('nav.agenda')}</NavLink>}
        </nav>
        <div className="session">
          <span>{auth.username}</span>
          <button type="button" onClick={() => void auth.logout()}>
            {t('auth.logout')}
          </button>
        </div>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
