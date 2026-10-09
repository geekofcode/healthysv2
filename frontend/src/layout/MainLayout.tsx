import {getPreferences} from '../api/preferences';
import {ThemeSwitch} from '../components/ThemeSwitch';
import {Breadcrumbs} from '../components/Breadcrumbs';
import {OrganizationContext} from '../components/OrganizationContext';
import {useEffect, useRef, useState} from 'react';
import {NavLink, Outlet, useLocation} from 'react-router-dom';
import {useQuery} from '@tanstack/react-query';
import {canAccessPath} from '../auth/roles';
import {useAuth} from '../auth/AuthContext';
import {keycloak} from '../auth/keycloak';
import {getMe} from '../api/persons';
import {useTranslation} from 'react-i18next';
import {NotificationBell} from '../components/NotificationBell';

export function MainLayout() {
  const auth = useAuth();
  const {t} = useTranslation();
  const location = useLocation();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [imageFailed, setImageFailed] = useState(false);
  const profileRef = useRef<HTMLDivElement>(null);
  const avatarRef = useRef<HTMLButtonElement>(null);
  const navigationRef = useRef<HTMLButtonElement>(null);
  const person = useQuery({queryKey: ['person', 'me'], queryFn: getMe});
  const preferences = useQuery({queryKey:['person','preferences'],queryFn:getPreferences});
  const fullName = person.data ? [person.data.firstName, person.data.middleName, person.data.lastName].filter(Boolean).join(' ') : String(keycloak.tokenParsed?.name || auth.username || t('nav.profile'));
  const picture = preferences.data?.avatarUrl ?? undefined;

  useEffect(() => {setSidebarOpen(false); setProfileOpen(false);}, [location.pathname]);
  useEffect(() => {setImageFailed(false);}, [picture]);
  useEffect(() => {
    const outside = (event: PointerEvent) => {
      if (!profileRef.current?.contains(event.target as Node)) setProfileOpen(false);
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      if (profileOpen) {setProfileOpen(false); avatarRef.current?.focus();}
      if (sidebarOpen) {setSidebarOpen(false); navigationRef.current?.focus();}
    };
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', escape);
    return () => {document.removeEventListener('pointerdown', outside); document.removeEventListener('keydown', escape);};
  }, [profileOpen, sidebarOpen]);

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">{t('shell.skipContent')}</a>
      <header className="topbar">
        <div className="brand-group">
          <button ref={navigationRef} type="button" className="sidebar-toggle" aria-label={t('shell.toggleNavigation')} aria-controls="main-navigation" aria-expanded={sidebarOpen} onClick={() => setSidebarOpen(!sidebarOpen)}>☰</button>
          <NavLink className="brand" to="/">HEALTH'YS</NavLink>
        </div>
        <div className="session">
          <span className="session-name">{fullName}</span>
          <ThemeSwitch authenticated />
          <NotificationBell />
          <div className="profile-menu" ref={profileRef} onBlur={event => {if (!event.currentTarget.contains(event.relatedTarget as Node)) setProfileOpen(false);}}>
            <button ref={avatarRef} type="button" className="profile-avatar" aria-label={t('shell.accountMenu')} aria-expanded={profileOpen} aria-controls="account-dropdown" onClick={() => setProfileOpen(!profileOpen)}>
              {picture && !imageFailed ? <img src={picture} alt="" referrerPolicy="no-referrer" onError={() => setImageFailed(true)} /> : <span aria-hidden="true">{Array.from(fullName.trim())[0]?.toLocaleUpperCase() || '?'}</span>}
            </button>
            {profileOpen && <div className="profile-dropdown" id="account-dropdown">
              <div className="profile-summary"><strong>{fullName}</strong><span>{auth.username}</span></div>
              <NavLink to="/me">{t('nav.profile')}</NavLink>
          {auth.roles.includes('PATIENT')&&<NavLink to="/me/access">{t('access.title')}</NavLink>}
          <NavLink to="/professional-onboarding">{t('onboarding.title')}</NavLink>
              <button type="button" onClick={() => {setProfileOpen(false); void auth.logout();}}>{t('auth.logout')}</button>
            </div>}
          </div>
        </div>
      </header>
      <div className="app-body">
        <aside className={`sidebar${sidebarOpen ? ' is-open' : ''}`}>
        <OrganizationContext />
        <nav id="main-navigation" aria-label={t('nav.main')}>
          <NavLink to="/" end>{t('nav.home')}</NavLink>
          <NavLink to="/me">{t('nav.profile')}</NavLink>
          {auth.roles.includes('PATIENT')&&<NavLink to="/me/access">{t('access.title')}</NavLink>}
          <NavLink to="/professional-onboarding">{t('onboarding.title')}</NavLink>
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
        </aside>
        {sidebarOpen && <button className="sidebar-backdrop" type="button" aria-label={t('shell.closeNavigation')} onClick={() => {setSidebarOpen(false); navigationRef.current?.focus();}} />}
        <main className="content" id="main-content" tabIndex={-1}><Breadcrumbs /><Outlet /></main>
      </div>
    </div>
  );
}
