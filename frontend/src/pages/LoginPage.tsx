import {useLocation} from 'react-router-dom';

import {useAuth} from '../auth/AuthContext';

export function LoginPage() {
  const auth = useAuth();
  const location = useLocation();
  const from = (location.state as {from?: string} | null)?.from ?? '/';

  if (!auth.initialized) {
    return <main className="centered">Initialisation de Keycloak…</main>;
  }

  return (
    <main className="login-page">
      <section className="login-card">
        <p className="eyebrow">Portail sécurisé</p>
        <h1>Bienvenue sur HEALTH'YS</h1>
        <p>
          Connectez-vous avec votre compte HEALTH'YS pour accéder
          à votre espace santé.
        </p>
        <button
          type="button"
          onClick={() => void auth.login(
            `${window.location.origin}${from}`,
          )}
        >
          Se connecter
        </button>
      </section>
    </main>
  );
}
