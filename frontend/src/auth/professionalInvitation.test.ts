// @vitest-environment jsdom
import {afterEach,describe,expect,it} from 'vitest';
import {captureProfessionalInvitation,clearProfessionalInvitation,storedProfessionalInvitation} from './professionalInvitation';
afterEach(()=>{sessionStorage.clear();window.history.replaceState({},'','/');});
describe('invitation authentication handoff',()=>{it('keeps invitation out of URL and retains it across login callback',()=>{window.history.replaceState({},'','/professional-invitation#token=secret');captureProfessionalInvitation();expect(window.location.hash).toBe('');expect(storedProfessionalInvitation()).toBe('secret');window.history.replaceState({},'','/professional-invitation#code=oauth-code&state=oauth-state');captureProfessionalInvitation();expect(window.location.hash).toContain('code=oauth-code');expect(storedProfessionalInvitation()).toBe('secret');clearProfessionalInvitation();expect(storedProfessionalInvitation()).toBe('');});});
