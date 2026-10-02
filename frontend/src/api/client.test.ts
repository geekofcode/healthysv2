// @vitest-environment jsdom
import {afterEach, describe, expect, it, vi} from 'vitest';

vi.mock('../auth/keycloak', () => ({
  validAccessToken: vi.fn().mockResolvedValue('access-token'),
}));

vi.mock('../i18n', () => ({
  default: {
    resolvedLanguage: 'fr',
    t: (key: string) => key,
  },
}));

import {apiRequest, ApiError} from './client';

describe('apiRequest', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('signals an unauthorized session once without starting a redirect', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(null, {status: 401}),
    );
    const unauthorized = vi.fn();
    window.addEventListener('healthys:unauthorized', unauthorized);

    await expect(apiRequest('/api/v1/persons/me')).rejects.toEqual(
      expect.objectContaining<Partial<ApiError>>({
        status: 401,
        code: 'UNAUTHORIZED',
      }),
    );

    expect(unauthorized).toHaveBeenCalledTimes(1);
    window.removeEventListener('healthys:unauthorized', unauthorized);
  });
});
