// @vitest-environment jsdom
import {afterEach, describe, expect, it, vi} from 'vitest';

vi.mock('../auth/keycloak', () => ({
  keycloak:{tokenParsed:{sub:'test-user'}},
  validAccessToken: vi.fn().mockResolvedValue('access-token'),
}));

vi.mock('../i18n', () => ({
  default: {
    resolvedLanguage: 'fr',
    t: (key: string) => key,
  },
}));

import {apiRequest, ApiError} from './client';
import {selectProfile} from '../auth/profileContext';

describe('apiRequest', () => {
  afterEach(() => {
    vi.restoreAllMocks();sessionStorage.clear();
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

it('sends exactly the chosen profile and its organization without accumulating another profile',async()=>{const fetchMock=vi.spyOn(globalThis,'fetch').mockImplementation(async()=>new Response('{}',{status:200}));selectProfile('test-user',{id:'doctor-a',organizationId:'hospital-a',type:'DOCTOR'});await apiRequest('/care/visits');let headers=fetchMock.mock.calls[0]![1]!.headers as Headers;expect(headers.get('X-Profile-ID')).toBe('doctor-a');expect(headers.get('X-Organization-ID')).toBe('hospital-a');selectProfile('test-user',{id:'cashier-b',organizationId:'hospital-b',type:'CASHIER'});await apiRequest('/billing');headers=fetchMock.mock.calls[1]![1]!.headers as Headers;expect(headers.get('X-Profile-ID')).toBe('cashier-b');expect(headers.get('X-Organization-ID')).toBe('hospital-b');selectProfile('test-user',{id:'patient',organizationId:null,type:'PATIENT'});await apiRequest('/me');headers=fetchMock.mock.calls[2]![1]!.headers as Headers;expect(headers.get('X-Profile-ID')).toBe('patient');expect(headers.has('X-Organization-ID')).toBe(false);sessionStorage.clear();vi.restoreAllMocks();});
it('discovers owned profiles without sending a stale selected profile header',async()=>{const fetchMock=vi.spyOn(globalThis,'fetch').mockImplementation(async()=>new Response('[]',{status:200}));selectProfile('test-user',{id:'revoked',organizationId:'hospital'});await apiRequest('/me/access-profiles');const headers=fetchMock.mock.calls[0]![1]!.headers as Headers;expect(headers.has('X-Profile-ID')).toBe(false);expect(headers.has('X-Organization-ID')).toBe(false);sessionStorage.clear();vi.restoreAllMocks();});
it('rejects a late response from a profile that was switched while the request was pending',async()=>{let finish!:(response:Response)=>void;vi.spyOn(globalThis,'fetch').mockImplementation(()=>new Promise<Response>(resolve=>{finish=resolve;}));selectProfile('test-user',{id:'doctor-a',organizationId:'hospital-a'});const pending=apiRequest('/patients/p');await vi.waitFor(()=>expect(finish).toBeDefined());selectProfile('test-user',{id:'doctor-b',organizationId:'hospital-b'});finish(new Response('{"medicalRecord":"old"}',{status:200}));await expect(pending).rejects.toMatchObject({code:'PROFILE_CHANGED'});sessionStorage.clear();vi.restoreAllMocks();});

it('keeps self deletion status accessible without a revoked profile header',async()=>{const fetchMock=vi.spyOn(globalThis,'fetch').mockImplementation(async()=>new Response('[]',{status:200}));selectProfile('test-user',{id:'deleted-doctor',organizationId:'old-hospital'});await apiRequest('/me/data-deletion');await apiRequest('/me/data-deletion/request-id');await apiRequest('/persons/me');for(const call of fetchMock.mock.calls){const headers=call[1]!.headers as Headers;expect(headers.has('X-Profile-ID')).toBe(false);expect(headers.has('X-Organization-ID')).toBe(false);}sessionStorage.clear();vi.restoreAllMocks();});
