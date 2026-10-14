import {expect,it} from 'vitest';
import messages from './contractEditor.json';
it('provides complete matching French and English rich-editor translations',()=>{expect(Object.keys(messages.fr).sort()).toEqual(Object.keys(messages.en).sort());for(const key of Object.keys(messages.fr) as (keyof typeof messages.fr)[]){expect(messages.fr[key].trim()).not.toBe('');expect(messages.en[key].trim()).not.toBe('');}});
