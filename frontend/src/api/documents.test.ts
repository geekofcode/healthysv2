import {describe,expect,it,vi} from 'vitest';
vi.mock('./client',()=>({apiRequest:vi.fn()}));vi.mock('../auth/keycloak',()=>({validAccessToken:vi.fn()}));
import {apiRequest} from './client';import {listDocuments,uploadDocument} from './documents';
describe('documents API',()=>{it('lists patient documents with stable pagination',()=>{listDocuments('patient 1');expect(apiRequest).toHaveBeenCalledWith('/documents?patientId=patient%201&size=20&page=0&sort=uploadedAt,desc')});it('builds a multipart upload without forcing a content type',()=>{const file=new File(['result'],'result.pdf',{type:'application/pdf'});uploadDocument('patient-1','category-1',file);expect(apiRequest).toHaveBeenCalledWith('/documents?patientId=patient-1&categoryId=category-1',expect.objectContaining({method:'POST',body:expect.any(FormData)}))})});

it('keeps patient ownership on subsequent document pages',()=>{listDocuments('patient 1',2);expect(apiRequest).toHaveBeenLastCalledWith('/documents?patientId=patient%201&size=20&page=2&sort=uploadedAt,desc')});
