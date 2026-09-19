import {NavLink, Outlet} from 'react-router-dom';

import {useAuth} from '../auth/AuthContext';

export function MainLayout() {
  const auth = useAuth();

  return (
    <div className="app-shell">
      <header className="topbar">
        <NavLink className="brand" to="/">HEALTH'YS</NavLink>
        <nav aria-label="Navigation principale">
          <NavLink to="/">Accueil</NavLink>
          <NavLink to="/me">Mon profil</NavLink>
        </nav>
        <div className="session">
          <span>{auth.username}</span>
          <button type="button" onClick={() => void auth.logout()}>
            Déconnexion
          </button>
        </div>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
