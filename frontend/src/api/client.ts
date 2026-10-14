import {selectedOrganization} from '../auth/organizationContext';
import {selectedProfile} from '../auth/profileContext';
import {keycloak,validAccessToken} from '../auth/keycloak';
import i18n from '../i18n';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

export class ApiError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

type ErrorPayload = {
  code?: string;
  message?: string;
};

export async function apiRequest<T>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  const subject=keycloak?.tokenParsed?.sub;
  const profile=selectedProfile(subject);
  const contextIndependent=path==='/me/access-profiles'||path==='/me/patient-profiles'||path.startsWith('/me/profile-contracts/')||path==='/persons/me'||path==='/me/data-deletion'||path.startsWith('/me/data-deletion/');
  const token = await validAccessToken();
  if(!contextIndependent&&JSON.stringify(selectedProfile(subject))!==JSON.stringify(profile))throw new ApiError(409,'PROFILE_CHANGED','The active profile changed. Reload this view.');
  const headers = new Headers(init.headers);
  headers.set('Authorization', `Bearer ${token}`);
  headers.set('Accept', 'application/json');
  const organizationId=profile?profile.organizationId:selectedOrganization(subject);
  headers.delete('X-Profile-ID');headers.delete('X-Organization-ID');
  if(!contextIndependent&&profile)headers.set('X-Profile-ID',profile.id);
  if(!contextIndependent&&organizationId&&organizationId!=='independent')headers.set('X-Organization-ID',organizationId);
  headers.set('Accept-Language', i18n.resolvedLanguage ?? 'en');

  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers,
  });

  if(!contextIndependent&&JSON.stringify(selectedProfile(subject))!==JSON.stringify(profile))throw new ApiError(409,'PROFILE_CHANGED','The active profile changed. Reload this view.');

  if (response.status === 401) {
    window.dispatchEvent(new Event('healthys:unauthorized'));
    throw new ApiError(401, 'UNAUTHORIZED', i18n.t('errors.sessionExpired'));
  }

  if (!response.ok) {
    const payload = await readError(response);
    throw new ApiError(
      response.status,
      payload.code ?? 'HTTP_ERROR',
      payload.message
        ?? (response.status === 403
          ? i18n.t('errors.forbidden')
          : i18n.t('errors.requestFailed')),
    );
  }

  if (response.status === 204) {
    return undefined as T;
  }
  const result=await response.json() as T;
  if(!contextIndependent&&JSON.stringify(selectedProfile(subject))!==JSON.stringify(profile))throw new ApiError(409,'PROFILE_CHANGED','The active profile changed. Reload this view.');
  return result;
}

async function readError(response: Response): Promise<ErrorPayload> {
  try {
    return await response.json() as ErrorPayload;
  } catch {
    return {};
  }
}
