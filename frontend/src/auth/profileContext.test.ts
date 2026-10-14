// @vitest-environment jsdom
import {afterEach,expect,it} from 'vitest';
import {selectedProfile,selectProfile} from './profileContext';
import {selectedOrganization} from './organizationContext';
afterEach(()=>sessionStorage.clear());
it('keeps a unique profile per account and synchronizes its organization',()=>{selectProfile('person',{id:'doctor-a',type:'DOCTOR',organizationId:'hospital-a'});expect(selectedProfile('person')).toEqual({id:'doctor-a',type:'DOCTOR',organizationId:'hospital-a',confirmed:true,session:undefined});expect(selectedProfile('another')).toBeUndefined();expect(selectedOrganization('person')).toBe('hospital-a');selectProfile('person',{id:'doctor-b',type:'DOCTOR',organizationId:'hospital-b'});expect(selectedProfile('person')?.id).toBe('doctor-b');expect(selectedOrganization('person')).toBe('hospital-b');});
it('clears hospital context for a personal or platform profile',()=>{selectProfile('person',{id:'cashier',organizationId:'hospital'});selectProfile('person',{id:'patient',organizationId:null,type:'PATIENT'});expect(selectedProfile('person')?.organizationId).toBeNull();expect(selectedOrganization('person')).toBe('independent');});
it('fails closed on a malformed persisted profile',()=>{sessionStorage.setItem('healthys:profile:person','{"id":3,"organizationId":"hospital"}');expect(selectedProfile('person')).toBeUndefined();});
