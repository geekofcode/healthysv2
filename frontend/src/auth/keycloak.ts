import Keycloak from 'keycloak-js';

function requiredEnvironment(name: keyof ImportMetaEnv): string {
  const value = import.meta.env[name];
  if (!value) {
    throw new Error(`Missing frontend environment variable: ${name}`);
  }
  return value;
}

export const keycloak = new Keycloak({
  url: requiredEnvironment('VITE_KEYCLOAK_URL'),
  realm: requiredEnvironment('VITE_KEYCLOAK_REALM'),
  clientId: requiredEnvironment('VITE_KEYCLOAK_CLIENT_ID'),
});

let initialization: Promise<boolean> | undefined;

export function initializeKeycloak(): Promise<boolean> {
  initialization ??= keycloak.init({
    onLoad: 'check-sso',
    pkceMethod: 'S256',
    checkLoginIframe: false,
    silentCheckSsoRedirectUri:
      `${window.location.origin}/silent-check-sso.html`,
  });
  return initialization;
}

export async function validAccessToken(): Promise<string> {
  if (!keycloak.authenticated) {
    throw new Error('The user is not authenticated');
  }

  await keycloak.updateToken(30);
  if (!keycloak.token) {
    throw new Error('Keycloak did not provide an access token');
  }
  return keycloak.token;
}
