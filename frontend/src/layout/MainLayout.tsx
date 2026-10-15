import {useSelectedPatientPhoto} from '../components/PatientPhoto';
import {ProfileNavigation} from '../components/ProfileNavigation';
import {ProfileContractBoundary} from '../auth/ProfileContractBoundary';
import {AccessProfileSelector} from '../components/AccessProfileSelector';
import {HospitalPermissionProvider,HospitalOperationBoundary} from '../auth/HospitalPermissionProvider';
import {LicenseBoundary} from '../components/LicenseBoundary';
import {getPreferences} from '../api/preferences';
import {Breadcrumbs} from '../components/Breadcrumbs';
import {useEffect, useRef, useState} from 'react';
import {NavLink, Outlet, useLocation} from 'react-router-dom';
import {useQuery} from '@tanstack/react-query';
import {useAuth} from '../auth/AuthContext';
import {keycloak} from '../auth/keycloak';
import {getMe} from '../api/persons';
import {useTranslation} from 'react-i18next';
import {NotificationBell} from '../components/NotificationBell';

export function MainLayout(){const auth=useAuth();return <ProfileContractBoundary><HospitalPermissionProvider key={`${auth.activeProfile?.id??'none'}:${auth.activeProfile?.type??''}`}><MainLayoutContent/></HospitalPermissionProvider></ProfileContractBoundary>;}
function MainLayoutContent() {
  const auth = useAuth();
  const {t,i18n} = useTranslation();
  const location = useLocation();
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [imageFailed, setImageFailed] = useState(false);
  const profileRef = useRef<HTMLDivElement>(null);
  const avatarRef = useRef<HTMLButtonElement>(null);
  const navigationRef = useRef<HTMLButtonElement>(null);
  const person = useQuery({queryKey: ['person', 'me'], queryFn: getMe});
  const patientPhoto=useSelectedPatientPhoto(auth.roles.includes('PATIENT'));
  const preferences = useQuery({queryKey:['person','preferences'],queryFn:getPreferences});
  const fullName = auth.roles.includes('PATIENT')?(auth.activeProfile?.label??t('nav.profile')):person.data ? [person.data.firstName, person.data.middleName, person.data.lastName].filter(Boolean).join(' ') : String(keycloak.tokenParsed?.name || auth.username || t('nav.profile'));
  const picture = auth.roles.includes('PATIENT')?patientPhoto.url:preferences.data?.avatarUrl??undefined;

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

          {!auth.roles.includes('PLATFORM_ADMIN')&&<NotificationBell />}
          <div className="profile-menu" ref={profileRef} onBlur={event => {if (!event.currentTarget.contains(event.relatedTarget as Node)) setProfileOpen(false);}}>
            <button ref={avatarRef} type="button" className="profile-avatar" aria-label={t('shell.accountMenu')} aria-expanded={profileOpen} aria-controls="account-dropdown" onClick={() => setProfileOpen(!profileOpen)}>
              {picture && !imageFailed ? <img src={picture} alt="" referrerPolicy="no-referrer" onError={() => setImageFailed(true)} /> : <span aria-hidden="true">{Array.from(fullName.trim())[0]?.toLocaleUpperCase() || '?'}</span>}
            </button>
            {profileOpen && <div className="profile-dropdown" id="account-dropdown">
              <div className="profile-summary"><strong>{fullName}</strong><span>{auth.username}</span></div>
              {auth.activeProfile&&<NavLink to={`/profiles/${auth.activeProfile.id}/contract`}>{i18n.language.startsWith('fr')?'Mon contrat':'My contract'}</NavLink>}<NavLink to="/me">{t('nav.profile')}</NavLink>{!auth.roles.includes('PLATFORM_ADMIN')&&<NavLink to="/me/team-invitations">{i18n.language.startsWith('fr')?'Invitations d’établissement':'Organization invitations'}</NavLink>}
          {!auth.roles.includes('PLATFORM_ADMIN')&&auth.roles.includes('PATIENT')&&<NavLink to="/me/access">{t('access.title')}</NavLink>}
              <button type="button" onClick={() => {setProfileOpen(false); void auth.logout();}}>{t('auth.logout')}</button>
            </div>}
          </div>
        </div>
      </header>
      <div className="app-body">
        <aside className={`sidebar${sidebarOpen ? ' is-open' : ''}`}>
        <ProfileNavigation/>
        </aside>
        {sidebarOpen && <button className="sidebar-backdrop" type="button" aria-label={t('shell.closeNavigation')} onClick={() => {setSidebarOpen(false); navigationRef.current?.focus();}} />}
        <main className="content" id="main-content" tabIndex={-1}><Breadcrumbs /><LicenseBoundary><HospitalOperationBoundary><Outlet /></HospitalOperationBoundary></LicenseBoundary></main>
      </div>
    </div>
  );
}
