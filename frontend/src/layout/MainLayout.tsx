import {LanguageSwitch} from '../components/LanguageSwitch';
import {ProfileContractBoundary} from '../auth/ProfileContractBoundary';
import {AccessProfileSelector} from '../components/AccessProfileSelector';
import {HospitalPermissionProvider,HospitalOperationBoundary} from '../auth/HospitalPermissionProvider';
import {useHospitalPermission} from '../auth/HospitalPermissions';
import {LicenseBoundary} from '../components/LicenseBoundary';
import {getPreferences} from '../api/preferences';
import {ThemeSwitch} from '../components/ThemeSwitch';
import {Breadcrumbs} from '../components/Breadcrumbs';
import {useEffect, useRef, useState} from 'react';
import {NavLink, Outlet, useLocation} from 'react-router-dom';
import {useQuery} from '@tanstack/react-query';
import {canAccessPath} from '../auth/roles';
import {useAuth} from '../auth/AuthContext';
import {keycloak} from '../auth/keycloak';
import {getMe} from '../api/persons';
import {useTranslation} from 'react-i18next';
import {NotificationBell} from '../components/NotificationBell';

export function MainLayout(){const auth=useAuth();return <ProfileContractBoundary><HospitalPermissionProvider key={`${auth.activeProfile?.id??'none'}:${auth.activeProfile?.type??''}`}><MainLayoutContent/></HospitalPermissionProvider></ProfileContractBoundary>;}
function MainLayoutContent() {
  const auth = useAuth();
  const employees=useHospitalPermission('employees','READ');
  const patients=useHospitalPermission('patients','READ');
  const schedule=useHospitalPermission('scheduling','READ');
  const reports=useHospitalPermission('reports','READ');
  const documents=useHospitalPermission('documents','READ');
  const cashreports=useHospitalPermission('cashreports','READ');
  const invoices=useHospitalPermission('invoices','READ');
  const {t,i18n} = useTranslation();
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
          <AccessProfileSelector />
          <LanguageSwitch /><ThemeSwitch authenticated />
          {!auth.roles.includes('PLATFORM_ADMIN')&&<NotificationBell />}
          <div className="profile-menu" ref={profileRef} onBlur={event => {if (!event.currentTarget.contains(event.relatedTarget as Node)) setProfileOpen(false);}}>
            <button ref={avatarRef} type="button" className="profile-avatar" aria-label={t('shell.accountMenu')} aria-expanded={profileOpen} aria-controls="account-dropdown" onClick={() => setProfileOpen(!profileOpen)}>
              {picture && !imageFailed ? <img src={picture} alt="" referrerPolicy="no-referrer" onError={() => setImageFailed(true)} /> : <span aria-hidden="true">{Array.from(fullName.trim())[0]?.toLocaleUpperCase() || '?'}</span>}
            </button>
            {profileOpen && <div className="profile-dropdown" id="account-dropdown">
              <div className="profile-summary"><strong>{fullName}</strong><span>{auth.username}</span></div>
              {auth.activeProfile&&<NavLink to={`/profiles/${auth.activeProfile.id}/contract`}>{i18n.language.startsWith('fr')?'Mon contrat':'My contract'}</NavLink>}<NavLink to="/me">{t('nav.profile')}</NavLink>{!auth.roles.includes('PLATFORM_ADMIN')&&<NavLink to="/me/team-invitations">{i18n.language.startsWith('fr')?'Invitations d’établissement':'Organization invitations'}</NavLink>}
          {!auth.roles.includes('PLATFORM_ADMIN')&&auth.roles.includes('PATIENT')&&<NavLink to="/me/access">{t('access.title')}</NavLink>}
          {!auth.roles.includes('PLATFORM_ADMIN')&&<NavLink to="/professional-onboarding">{t('onboarding.title')}</NavLink>}
              <button type="button" onClick={() => {setProfileOpen(false); void auth.logout();}}>{t('auth.logout')}</button>
            </div>}
          </div>
        </div>
      </header>
      <div className="app-body">
        <aside className={`sidebar${sidebarOpen ? ' is-open' : ''}`}>
        {auth.roles.includes('PLATFORM_ADMIN')&&<NavLink to="/admin/accounts">{i18n.language.startsWith('fr')?'Comptes utilisateurs':'User accounts'}</NavLink>}
        <nav id="main-navigation" aria-label={t('nav.main')}>
          <NavLink to="/" end>{t('nav.home')}</NavLink>
          <NavLink to="/me">{t('nav.profile')}</NavLink>{!auth.roles.includes('PLATFORM_ADMIN')&&<NavLink to="/me/team-invitations">{i18n.language.startsWith('fr')?'Invitations d’établissement':'Organization invitations'}</NavLink>}
          {!auth.roles.includes('PLATFORM_ADMIN')&&auth.roles.includes('PATIENT')&&<NavLink to="/me/access">{t('access.title')}</NavLink>}
          {!auth.roles.includes('PLATFORM_ADMIN')&&<NavLink to="/professional-onboarding">{t('onboarding.title')}</NavLink>}
          {canAccessPath(auth.roles, '/organizations')&&<NavLink to="/organizations">{t('nav.organizations')}</NavLink>}
          {!auth.roles.includes('PLATFORM_ADMIN')&&employees&&canAccessPath(auth.roles,'/employees')&&<NavLink to="/employees">{i18n.language.startsWith('fr')?'Gestion des employés':'Employee management'}</NavLink>}
          {patients&&canAccessPath(auth.roles, '/patients')&&<NavLink to="/patients">{t('nav.patients')}</NavLink>}
          {schedule&&canAccessPath(auth.roles, '/agenda')&&<NavLink to="/agenda">{t('nav.agenda')}</NavLink>}
          {reports&&canAccessPath(auth.roles, '/consultations')&&<NavLink to="/consultations">{t('nav.consultations')}</NavLink>}
          {documents&&canAccessPath(auth.roles, '/documents')&&<NavLink to="/documents">{t('nav.documents')}</NavLink>}
          {invoices&&canAccessPath(auth.roles, '/billing')&&<NavLink to="/billing">{t('nav.billing')}</NavLink>}
          {!auth.roles.includes('PLATFORM_ADMIN')&&cashreports&&canAccessPath(auth.roles,'/billing')&&<NavLink to="/billing/cash">{i18n.language.startsWith('fr')?'Caisse':'Cash management'}</NavLink>}
          {canAccessPath(auth.roles,'/admin')&&<NavLink to="/admin/contract-templates">{i18n.language.startsWith('fr')?'Contrats et conditions':'Contracts and terms'}</NavLink>}
          {canAccessPath(auth.roles, '/admin')&&<NavLink to="/admin/licensing-plans">{i18n.language.startsWith('fr')?'Offres et licences':'Plans and licenses'}</NavLink>}
          {canAccessPath(auth.roles, '/admin')&&<NavLink to="/admin">{t('nav.admin')}</NavLink>}
        </nav>
        </aside>
        {sidebarOpen && <button className="sidebar-backdrop" type="button" aria-label={t('shell.closeNavigation')} onClick={() => {setSidebarOpen(false); navigationRef.current?.focus();}} />}
        <main className="content" id="main-content" tabIndex={-1}><Breadcrumbs /><LicenseBoundary><HospitalOperationBoundary><Outlet /></HospitalOperationBoundary></LicenseBoundary></main>
      </div>
    </div>
  );
}
