import {
  createContext,
  type ReactNode,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';

import {
  initializeKeycloak,
  keycloak,
  validAccessToken,
} from './keycloak';

type AuthContextValue = {
  initialized: boolean;
  authenticated: boolean;
  username?: string;
  roles: string[];
  hasAnyRole: (...roles: string[]) => boolean;
  login: (redirectUri?: string) => Promise<void>;
  register: (redirectUri?: string) => Promise<void>;
  logout: () => Promise<void>;
  getAccessToken: () => Promise<string>;
};

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({children}: {children: ReactNode}) {
  const [initialized, setInitialized] = useState(false);
  const [authenticated, setAuthenticated] = useState(false);

  useEffect(() => {
    let mounted = true;

    initializeKeycloak()
      .then((isAuthenticated) => {
        if (mounted) {
          setAuthenticated(isAuthenticated);
          setInitialized(true);
        }
      })
      .catch(() => {
        if (mounted) {
          setAuthenticated(false);
          setInitialized(true);
        }
      });

    keycloak.onAuthSuccess = () => setAuthenticated(true);
    keycloak.onAuthLogout = () => setAuthenticated(false);
    keycloak.onTokenExpired = () => {
      void keycloak.updateToken(30).catch(() => {
        setAuthenticated(false);
      });
    };

    const handleUnauthorized = () => {
      // A 401 can also mean that the API rejected a structurally valid token
      // (for example because its audience is missing). Redirecting immediately
      // to Keycloak would return the same token and create an endless loop.
      keycloak.clearToken();
      setAuthenticated(false);
    };
    window.addEventListener('healthys:unauthorized', handleUnauthorized);

    return () => {
      mounted = false;
      window.removeEventListener(
        'healthys:unauthorized',
        handleUnauthorized,
      );
    };
  }, []);

  const login = useCallback(async (redirectUri?: string) => {
    await keycloak.login({
      redirectUri: redirectUri ?? window.location.origin,
    });
  }, []);

  const logout = useCallback(async () => {
    await keycloak.logout({
      redirectUri: window.location.origin,
    });
  }, []);

  const register = useCallback(async (redirectUri?: string) => {
    await keycloak.register({
      redirectUri: redirectUri ?? `${window.location.origin}/registration/complete`,
    });
  }, []);

  const value = useMemo<AuthContextValue>(
    () => {
      const roles = keycloak.tokenParsed?.realm_access?.roles ?? [];
      return ({
      initialized,
      authenticated,
      username:
        keycloak.tokenParsed?.preferred_username as string | undefined,
      roles,
      hasAnyRole: (...requiredRoles) => requiredRoles.some(
        (role) => roles.includes(role),
      ),
      login,
      register,
      logout,
      getAccessToken: validAccessToken,
    });},
    [authenticated, initialized, login, logout, register],
  );

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider');
  }
  return context;
}
