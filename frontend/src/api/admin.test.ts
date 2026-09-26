import {beforeEach,describe,expect,it,vi} from 'vitest';
vi.mock('./client',()=>({apiRequest:vi.fn()}));
import {apiRequest} from './client';
import {getAuditDashboard,listAuditLogs,resolveSecurityEvent} from './admin';
describe('platform administration API',()=>{beforeEach(()=>vi.mocked(apiRequest).mockReset());it('serializes audit filters',()=>{listAuditLogs({module:'PATIENT',action:'UPDATE'});expect(apiRequest).toHaveBeenCalledWith('/admin/audit-logs?module=PATIENT&action=UPDATE&size=50')});it('loads dashboard period',()=>{getAuditDashboard(30);expect(apiRequest).toHaveBeenCalledWith('/admin/audit-dashboard?days=30')});it('resolves security events',()=>{resolveSecurityEvent('event-1');expect(apiRequest).toHaveBeenCalledWith('/admin/security-events/event-1/resolve',{method:'POST'})})});
