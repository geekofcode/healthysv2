import {beforeEach,describe,expect,it,vi} from 'vitest';
vi.mock('./client',()=>({apiRequest:vi.fn()}));
import {apiRequest} from './client';
import {hasVitalValue,searchDiagnosisCatalog} from './consultations';
import {listLabOrders,validReferenceRange} from './laboratory';
import {checkPatientAccess,validConsentGrantee} from './patientAccess';

describe('clinical web APIs',()=>{beforeEach(()=>vi.mocked(apiRequest).mockClear());it('enforces the consent grantee XOR rule',()=>{expect(validConsentGrantee('person','')).toBe(true);expect(validConsentGrantee('','organization')).toBe(true);expect(validConsentGrantee('person','organization')).toBe(false);expect(validConsentGrantee('','')).toBe(false)});it('serializes access checks',()=>{checkPatientAccess('patient-1','MEDICAL_RECORD','READ');expect(apiRequest).toHaveBeenCalledWith('/patients/patient-1/access?scope=MEDICAL_RECORD&action=READ')});it('requires at least one vital value',()=>{expect(hasVitalValue({temperature:'37.2'})).toBe(true);expect(hasVitalValue({temperature:'',measuredBy:'professional'})).toBe(false)});it('searches the diagnosis catalog',()=>{searchDiagnosisCatalog('malaria test');expect(apiRequest).toHaveBeenCalledWith('/diagnosis-catalog?query=malaria+test')});it('serializes laboratory filters and validates ranges',()=>{listLabOrders({patientId:'patient-1',organizationId:'',status:'ORDERED'});expect(apiRequest).toHaveBeenCalledWith('/lab-orders?patientId=patient-1&status=ORDERED&size=50&sort=orderedAt%2Cdesc');expect(validReferenceRange(2,5)).toBe(true);expect(validReferenceRange(5,2)).toBe(false)})});
