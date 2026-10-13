import {beforeEach,describe,expect,it,vi} from 'vitest';
vi.mock('./client',()=>({apiRequest:vi.fn()}));
import {apiRequest} from './client';
import {listAccessRequests,requestPatientAccess,emergencyPatientAccess,revokeDerivedAccess} from './patientAccess';
beforeEach(()=>vi.clearAllMocks());
describe('temporary access API',()=>{
 it('normalizes server audit timestamps and requester identity',async()=>{vi.mocked(apiRequest).mockResolvedValue([{id:'r1',requester_person_id:'staff',created_at:'2026-10-13T00:00:00Z',status:'PENDING'}]);expect(await listAccessRequests('p1')).toEqual([{id:'r1',requesterPersonId:'staff',createdAt:'2026-10-13T00:00:00Z',status:'PENDING'}]);});
 it('requests the full-record scope without selecting its own expiration',async()=>{await requestPatientAccess('p1','Suivi médical');expect(apiRequest).toHaveBeenCalledWith('/patients/p1/access-requests',{method:'POST',body:JSON.stringify({scope:'FULL_RECORD',reason:'Suivi médical'})});});
 it('submits emergency justification and explicit derived-access revocation',async()=>{await emergencyPatientAccess('p1','Urgence');await revokeDerivedAccess('p1','staff','hospital');expect(apiRequest).toHaveBeenCalledWith('/patients/p1/emergency-access',{method:'POST',body:JSON.stringify({reason:'Urgence'})});expect(apiRequest).toHaveBeenCalledWith('/patients/p1/access-revocations',{method:'POST',body:JSON.stringify({personId:'staff',organizationId:'hospital'})});});
});
