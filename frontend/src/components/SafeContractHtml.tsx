import {useMemo} from 'react';
import {sanitizeContractHtml} from '../contracts/contractHtml';
export function SafeContractHtml({html,className='contract-rich-content'}:{html:string;className?:string}){const safe=useMemo(()=>sanitizeContractHtml(html).html,[html]);return <div className={className} dangerouslySetInnerHTML={{__html:safe}}/>;}
