import {NavLink, Outlet} from 'react-router-dom';

import {canAccessPath} from '../auth/roles';
import {useAuth} from '../auth/AuthContext';
import {useTranslation} from 'react-i18next';
import {NotificationBell} from '../components/NotificationBell';

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
          {canAccessPath(auth.roles, '/organizations')&&<NavLink to="/organizations">{t('nav.organizations')}</NavLink>}
          {canAccessPath(auth.roles, '/professionals')&&<NavLink to="/professionals">{t('nav.professionals')}</NavLink>}
          {canAccessPath(auth.roles, '/patients')&&<NavLink to="/patients">{t('nav.patients')}</NavLink>}
          {canAccessPath(auth.roles, '/agenda')&&<NavLink to="/agenda">{t('nav.agenda')}</NavLink>}
          {canAccessPath(auth.roles, '/consultations/new')&&<NavLink to="/consultations/new">{t('nav.consultations')}</NavLink>}
          {canAccessPath(auth.roles, '/laboratory/orders')&&<NavLink to="/laboratory/orders">{t('nav.laboratory')}</NavLink>}
          {canAccessPath(auth.roles, '/maternal-child')&&<NavLink to="/maternal-child">{t('nav.maternalChild')}</NavLink>}
          {canAccessPath(auth.roles, '/documents')&&<NavLink to="/documents">{t('nav.documents')}</NavLink>}
          {canAccessPath(auth.roles, '/chat')&&<NavLink to="/chat">{t('nav.chat')}</NavLink>}
          {canAccessPath(auth.roles, '/teleconsultations')&&<NavLink to="/teleconsultations">{t('nav.teleconsultations')}</NavLink>}
          {canAccessPath(auth.roles, '/pharmacy/prescriptions')&&<NavLink to="/pharmacy/prescriptions">{t('nav.pharmacy')}</NavLink>}
          {canAccessPath(auth.roles, '/billing')&&<NavLink to="/billing">{t('nav.billing')}</NavLink>}
          {canAccessPath(auth.roles, '/admin')&&<NavLink to="/admin">{t('nav.admin')}</NavLink>}
        </nav>
        <div className="session">
          <NotificationBell />
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
