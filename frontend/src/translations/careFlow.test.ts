import {expect,it} from 'vitest';
import messages from './careFlow.json';
function flatten(value:Record<string,any>,prefix=''):Record<string,string>{return Object.fromEntries(Object.entries(value).flatMap(([key,item])=>typeof item==='string'?[[`${prefix}${key}`,item]]:Object.entries(flatten(item,`${prefix}${key}.`))));}
it('provides matching nonempty French and English care-workflow translations',()=>{const fr=flatten(messages.fr);const en=flatten(messages.en);expect(Object.keys(fr).sort()).toEqual(Object.keys(en).sort());for(const key of Object.keys(fr)){expect(fr[key]!.trim()).not.toBe('');expect(en[key]!.trim()).not.toBe('');}});
