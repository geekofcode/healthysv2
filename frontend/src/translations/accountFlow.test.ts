import {expect,it} from 'vitest';
import messages from './accountFlow.json';
it('provides matching French and English account-flow translations',()=>{
 expect(Object.keys(messages.fr).sort()).toEqual(Object.keys(messages.en).sort());
 for(const key of Object.keys(messages.fr) as (keyof typeof messages.fr)[]){expect(messages.fr[key].trim()).not.toBe('');expect(messages.en[key].trim()).not.toBe('');expect(messages.fr[key].match(/\{\{[^}]+\}\}/g)??[]).toEqual(messages.en[key].match(/\{\{[^}]+\}\}/g)??[]);}
});
