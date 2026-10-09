// @vitest-environment jsdom
import {beforeEach,describe,expect,it,vi} from 'vitest';
vi.mock('./client',()=>({apiRequest:vi.fn()}));
vi.mock('../auth/keycloak',()=>({validAccessToken:vi.fn()}));
import {apiRequest} from './client';
import {acceptInvitation,reviewDossier,saveDossier,uploadProof} from './professionalOnboarding';
beforeEach(()=>vi.clearAllMocks());
describe('professional onboarding transport',()=>{it('sends profession as data without requesting clinical role grants',()=>{saveDossier({profession:'medecin',licenseNumber:'ABC',issuingAuthority:'Order',countryId:'country'});expect(apiRequest).toHaveBeenCalledWith('/professional-onboarding/me',{method:'PUT',body:JSON.stringify({profession:'medecin',licenseNumber:'ABC',issuingAuthority:'Order',countryId:'country'})});});it('uploads evidence as multipart',()=>{const file=new File(['proof'],'proof.pdf',{type:'application/pdf'});uploadProof(file);const init=vi.mocked(apiRequest).mock.calls[0]?.[1];expect(init?.body).toBeInstanceOf(FormData);expect((init?.body as FormData).get('file')).toBe(file);});it('posts explicit review and invited token',()=>{reviewDossier('id','REJECT','Expired licence');acceptInvitation('secret');expect(apiRequest).toHaveBeenCalledWith('/professional-onboarding/requests/id/review',{method:'POST',body:JSON.stringify({decision:'REJECT',reason:'Expired licence'})});expect(apiRequest).toHaveBeenCalledWith('/professional-onboarding/invitations/accept',{method:'POST',body:JSON.stringify({token:'secret'})});});});
