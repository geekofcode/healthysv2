import {useQuery} from '@tanstack/react-query';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {listOrganizations, organizationKeys} from '../api/organizations';
import {useAuth} from '../auth/AuthContext';
import {ApiErrorMessage} from '../components/ApiErrorMessage';

export function OrganizationsPage(){
 const {t}=useTranslation(); const auth=useAuth();
 const query=useQuery({queryKey:organizationKeys.all,queryFn:listOrganizations});
 const canWrite=auth.hasAnyRole('PLATFORM_ADMIN','HOSPITAL_ADMIN');
 if(query.isPending)return <p>{t('organizations.loading')}</p>;
 if(query.isError)return <ApiErrorMessage error={query.error}/>;
 return <section><div className="page-heading"><h1>{t('organizations.title')}</h1>{canWrite&&<Link className="button" to="/organizations/new">{t('organizations.new')}</Link>}</div>
  {query.data.content.length===0?<p>{t('organizations.empty')}</p>:<table><thead><tr><th>{t('organizations.number')}</th><th>{t('organizations.name')}</th><th>{t('organizations.status')}</th></tr></thead><tbody>{query.data.content.map(o=><tr key={o.id}><td><Link to={`/organizations/${o.id}`}>{o.number}</Link></td><td>{o.name}</td><td>{t(`status.${o.status}`,o.status)}</td></tr>)}</tbody></table>}
 </section>;
}
