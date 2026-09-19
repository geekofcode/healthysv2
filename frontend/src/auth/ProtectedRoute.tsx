import {Navigate, Outlet, useLocation} from 'react-router-dom';

import {useAuth} from './AuthContext';
import {useTranslation} from 'react-i18next';

export function ProtectedRoute() {
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
        state={{from: location.pathname}}
      />
    );
  }

  return <Outlet />;
}
