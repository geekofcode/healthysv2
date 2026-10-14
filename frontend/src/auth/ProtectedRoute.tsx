import {captureProfessionalInvitation} from './professionalInvitation';
import {Navigate, Outlet, useLocation} from 'react-router-dom';

import {canAccessPath} from './roles';

import {useAuth} from './AuthContext';
import {useTranslation} from 'react-i18next';

export function ProtectedRoute() {
  captureProfessionalInvitation();
  const auth = useAuth();
  const location = useLocation();
  const {t} = useTranslation();

  if (!auth.initialized) {
    return <main className="centered">{t('auth.sessionInitializing')}</main>;
  }

  if (!auth.authenticated) {
    return (
      <Navigate
        to="/login"
        replace
        state={{from: location.pathname + location.search}}
      />
    );
  }

  const bootstrap=location.pathname.startsWith('/profiles/')||location.pathname==='/me/data-deletion'||location.pathname==='/me/team-invitations';
  if(!auth.activeProfile&&!bootstrap)return <Navigate to="/profiles/select" replace/>;

  if (!canAccessPath(auth.roles, location.pathname)) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}
