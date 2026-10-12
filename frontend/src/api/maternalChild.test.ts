import {beforeEach,describe,expect,it,vi}from'vitest';vi.mock('./client',()=>({apiRequest:vi.fn()}));import{apiRequest}from'./client';import{hasGrowthValue,listPregnancies,resolvePregnancyRisk}from'./maternalChild';describe('maternal-child API',()=>{beforeEach(()=>vi.mocked(apiRequest).mockClear());it('serializes the mother filter',()=>{listPregnancies('mother-1');expect(apiRequest).toHaveBeenCalledWith('/pregnancies?motherPatientId=mother-1')});it('resolves a pregnancy risk',()=>{resolvePregnancyRisk('pregnancy-1','risk-1');expect(apiRequest).toHaveBeenCalledWith('/pregnancies/pregnancy-1/risks/risk-1/resolve',{method:'POST'})});it('requires a growth value',()=>{expect(hasGrowthValue({weight:'4.2',height:''})).toBe(true);expect(hasGrowthValue({weight:'',height:'',headCircumference:''})).toBe(false)})});

import {getMyPregnancy,getMyChildHealthRecord,listMyPregnancies,listMyChildren} from './maternalChild';
describe('patient maternal projections',()=>{
 beforeEach(()=>vi.mocked(apiRequest).mockReset());
 it('uses self scoped lists without a supplied patient identifier',()=>{
  listMyPregnancies(2);listMyChildren(1);
  expect(apiRequest).toHaveBeenCalledWith('/patients/me/maternal-child/pregnancies?page=2&size=20');
  expect(apiRequest).toHaveBeenCalledWith('/patients/me/maternal-child/children?page=1&size=20');
 });
 it('maps documented patient measurement units and excludes private narrative',async()=>{
  vi.mocked(apiRequest).mockResolvedValueOnce({pregnancy:{id:'p',pregnancyNumber:'P1'},prenatalVisits:[{id:'v',weightKg:64}],delivery:{newborns:[{birthWeightKg:3.4,birthHeightCm:50,headCircumferenceCm:34}]}});
  const pregnancy=await getMyPregnancy('p');
  expect(apiRequest).toHaveBeenCalledWith('/patients/me/maternal-child/pregnancies/p');
  expect(pregnancy.prenatalVisits[0]).toMatchObject({weight:64});expect(pregnancy.risks).toEqual([]);expect(pregnancy.postpartumVisits).toEqual([]);expect(pregnancy.delivery?.newborns[0]).toMatchObject({birthWeight:3.4});
  vi.mocked(apiRequest).mockResolvedValueOnce({child:{id:'record',childPatientId:'child',status:'ACTIVE'},vaccinations:[],growthMeasurements:[{weightKg:5,heightCm:60,headCircumferenceCm:40}]});
  const child=await getMyChildHealthRecord('child');expect(child.growthMeasurements[0]).toMatchObject({weight:5,height:60,headCircumference:40});
 });
});
