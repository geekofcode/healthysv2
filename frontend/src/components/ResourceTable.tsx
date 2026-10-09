import type {ReactNode} from 'react';
import {useTranslation} from 'react-i18next';
import type {Page} from '../api/organizations';

export type TableColumn<T>={label:string;render:(row:T)=>ReactNode};
export function ResourceTable<T extends {id:string}>({data,columns,page,onPageChange,actions,empty}:{data:Page<T>;columns:TableColumn<T>[];page:number;onPageChange:(page:number)=>void;actions?:(row:T)=>ReactNode;empty:string}){
 const {t,i18n}=useTranslation();const fr=i18n.language.startsWith('fr');
 return <div className="resource-table"><div className="table-scroll"><table><thead><tr>{columns.map(c=><th key={c.label} scope="col">{c.label}</th>)}{actions&&<th scope="col">{t('common.actions')}</th>}</tr></thead><tbody>{data.content.map(row=><tr key={row.id}>{columns.map(c=><td key={c.label}>{c.render(row)}</td>)}{actions&&<td><div className="row-actions">{actions(row)}</div></td>}</tr>)}{!data.content.length&&<tr><td colSpan={columns.length+(actions?1:0)} className="empty-state">{empty}</td></tr>}</tbody></table></div><div className="table-pagination"><span>{data.page.totalElements} {fr?'résultats':'results'}</span><div><button type="button" className="secondary" disabled={page===0} onClick={()=>onPageChange(page-1)}>{fr?'Précédent':'Previous'}</button><span aria-live="polite"> {page+1} / {Math.max(1,data.page.totalPages)} </span><button type="button" className="secondary" disabled={data.page.last||page+1>=data.page.totalPages} onClick={()=>onPageChange(page+1)}>{fr?'Suivant':'Next'}</button></div></div></div>;
}
