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

  if (!canAccessPath(auth.roles, location.pathname)) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}
