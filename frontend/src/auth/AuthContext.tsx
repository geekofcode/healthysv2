import {useQuery,useQueryClient} from '@tanstack/react-query';
import {listAccessProfiles,type AccessProfile} from '../api/accessProfiles';
import {selectedProfile,selectProfile,clearProfile} from './profileContext';
import {rolesForProfile} from './roles';
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
  profiles:AccessProfile[];
  activeProfile?:AccessProfile;
  switchProfile:(id:string)=>Promise<void>;
  profilesError?:unknown;
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
  const client=useQueryClient();
  const subject=keycloak.tokenParsed?.sub;
  const sessionMarker=keycloak.tokenParsed?.sid??keycloak.tokenParsed?.session_state??keycloak.tokenParsed?.auth_time;
  const session=typeof sessionMarker==='string'||typeof sessionMarker==='number'?String(sessionMarker):undefined;
  const [profileId,setProfileId]=useState(()=>selectedProfile(subject)?.id);
  const [switching,setSwitching]=useState(false);
  const profiles=useQuery({queryKey:['access-profiles',subject],queryFn:listAccessProfiles,enabled:initialized&&authenticated&&!!subject,refetchInterval:30000,retry:false});
  const activeProfiles=(profiles.isError?[]:profiles.data??[]).filter(p=>p.active&&p.type!=='ACCOUNT');
  const storedSelection=selectedProfile(subject);
  const activeProfile=storedSelection?.confirmed&&(!session||storedSelection.session===session)?activeProfiles.find(p=>p.id===profileId):undefined;
  useEffect(()=>{const update=()=>setProfileId(selectedProfile(subject)?.id);window.addEventListener('healthys:profile-changed',update);return()=>window.removeEventListener('healthys:profile-changed',update);},[subject]);
  useEffect(()=>{if(!subject||!profiles.isSuccess)return;const stored=selectedProfile(subject);const available=profiles.data.filter(p=>p.active&&p.type!=='ACCOUNT');const current=stored?.confirmed&&(!session||stored.session===session)?available.find(p=>p.id===stored.id&&p.type===stored.type&&(p.organizationId??null)===(stored.organizationId??null)):undefined;if(current){setProfileId(current.id);}else{void client.cancelQueries({predicate:q=>q.queryKey[0]!=='access-profiles'});client.removeQueries({predicate:q=>q.queryKey[0]!=='access-profiles'});client.getMutationCache().clear();if(stored)clearProfile(subject);setProfileId(undefined);}},[subject,profiles.data,profiles.isSuccess,client,session]);
  const switchProfile=useCallback(async(id:string)=>{const next=profiles.data?.find(p=>p.id===id&&p.active);if(!subject||!next)throw new Error('Unavailable profile');const previous=selectedProfile(subject);if(next.id===previous?.id&&previous.confirmed&&(!session||previous.session===session))return;setSwitching(true);try{await client.cancelQueries();client.clear();selectProfile(subject,{...next,session});setProfileId(next.id);client.setQueryData(['access-profiles',subject],profiles.data); }finally{setSwitching(false);}},[client,profiles.data,subject,session]);

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
    keycloak.onAuthLogout = () => {client.clear();clearProfile(keycloak.tokenParsed?.sub);setAuthenticated(false);};
    keycloak.onTokenExpired = () => {
      void keycloak.updateToken(30).catch(() => {
        setAuthenticated(false);
      });
    };

    const handleUnauthorized = () => {
      // A 401 can also mean that the API rejected a structurally valid token
      // (for example because its audience is missing). Redirecting immediately
      // to Keycloak would return the same token and create an endless loop.
      client.clear();clearProfile(keycloak.tokenParsed?.sub);
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
    clearProfile(subject);
    await keycloak.login({
      redirectUri: redirectUri ?? window.location.origin,
    });
  }, [subject]);

  const logout = useCallback(async () => {
    await client.cancelQueries();client.clear();clearProfile(subject);
    await keycloak.logout({
      redirectUri: window.location.origin,
    });
  }, [client,subject]);

  const register = useCallback(async (redirectUri?: string) => {
    await keycloak.register({
      redirectUri: redirectUri ?? `${window.location.origin}/registration/complete`,
    });
  }, []);

  const value = useMemo<AuthContextValue>(
    () => {
      const roles=rolesForProfile(activeProfile);
      return ({
      initialized:initialized&&!switching&&(!authenticated||!subject||profiles.isSuccess||profiles.isError),
      authenticated,
      profiles:activeProfiles,activeProfile,switchProfile,profilesError:profiles.error,
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
    [authenticated, initialized, login, logout, register,activeProfile,activeProfiles,switchProfile,switching,profiles.error,profiles.isSuccess,subject],
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
