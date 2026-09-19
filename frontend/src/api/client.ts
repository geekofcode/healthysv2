import {validAccessToken} from '../auth/keycloak';

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
  const token = await validAccessToken();
  const headers = new Headers(init.headers);
  headers.set('Authorization', `Bearer ${token}`);
  headers.set('Accept', 'application/json');

  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers,
  });

  if (response.status === 401) {
    window.dispatchEvent(new Event('healthys:unauthorized'));
    throw new ApiError(401, 'UNAUTHORIZED', 'Votre session a expiré.');
  }

  if (!response.ok) {
    const payload = await readError(response);
    throw new ApiError(
      response.status,
      payload.code ?? 'HTTP_ERROR',
      payload.message
        ?? (response.status === 403
          ? 'Vous ne disposez pas des droits nécessaires.'
          : 'La requête a échoué.'),
    );
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

async function readError(response: Response): Promise<ErrorPayload> {
  try {
    return await response.json() as ErrorPayload;
  } catch {
    return {};
  }
}
