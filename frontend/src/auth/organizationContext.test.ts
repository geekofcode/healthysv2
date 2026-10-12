// @vitest-environment jsdom
import {afterEach,describe,expect,it} from 'vitest';
import {selectOrganization,selectedOrganization} from './organizationContext';
afterEach(()=>sessionStorage.clear());
describe('organization context',()=>{it('never reuses a previous account selection',()=>{selectOrganization('doctor-a','hospital-a');expect(selectedOrganization('doctor-a')).toBe('hospital-a');expect(selectedOrganization('doctor-b')).toBeUndefined();expect(selectedOrganization()).toBeUndefined();});it('clears explicit context for independent practice',()=>{selectOrganization('doctor-a','hospital-a');selectOrganization('doctor-a','');expect(selectedOrganization('doctor-a')).toBe('independent');});});
