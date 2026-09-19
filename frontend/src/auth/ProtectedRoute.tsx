import {Navigate, Outlet, useLocation} from 'react-router-dom';

import {useAuth} from './AuthContext';

export function ProtectedRoute() {
  const auth = useAuth();
  const location = useLocation();

  if (!auth.initialized) {
    return <main className="centered">Initialisation de la session…</main>;
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
