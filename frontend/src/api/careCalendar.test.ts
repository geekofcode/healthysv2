import {expect,it,vi} from 'vitest';
vi.mock('./client',()=>({apiRequest:vi.fn()}));
import {apiRequest} from './client';
import {getCareCalendar,reorderCareQueue,setCarePresence} from './careCalendar';
import {scheduleCareVisit} from './careEpisodes';
it('calendar is scoped to selected date and doctor',()=>{
 getCareCalendar('2026-10-13','doctor');
 expect(apiRequest).toHaveBeenLastCalledWith('/care/calendar?date=2026-10-13&professionalId=doctor');
});
it('presence and reorder retain optimistic queue version',()=>{
 setCarePresence({visitId:'v',present:true,expectedVersion:5});
 expect(apiRequest).toHaveBeenLastCalledWith('/care/queue/presence',{method:'POST',body:JSON.stringify({visitId:'v',present:true,expectedVersion:5})});
 reorderCareQueue({professionalId:'d',date:'2026-10-13',visitIds:['v2','v1'],expectedVersion:6});
 expect(apiRequest).toHaveBeenLastCalledWith('/care/queue/order',{method:'PUT',body:JSON.stringify({professionalId:'d',date:'2026-10-13',visitIds:['v2','v1'],expectedVersion:6})});
});
it('rescheduling modifies same visit with its version',()=>{
 scheduleCareVisit('v',{professionalId:'d2',scheduledAt:'2026-10-14T10:00:00Z',version:2});
 expect(apiRequest).toHaveBeenLastCalledWith('/care/visits/v/schedule',{method:'PUT',body:JSON.stringify({professionalId:'d2',scheduledAt:'2026-10-14T10:00:00Z',version:2})});
});
