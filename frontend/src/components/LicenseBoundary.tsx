import {ReactNode} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Link,useLocation} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {useAuth} from '../auth/AuthContext';
import {keycloak} from '../auth/keycloak';
import {selectedOrganization} from '../auth/organizationContext';
import {getEntitlements,licensingKeys} from '../api/licensing';
import {ApiErrorMessage} from './ApiErrorMessage';
const operational=['care','patients','agenda','consultations','laboratory','maternal-child','documents','pharmacy','billing','chat','teleconsultations'];
export function LicenseBoundary({children}:{children:ReactNode}){const auth=useAuth();const location=useLocation();const {i18n}=useTranslation();const fr=i18n.language.startsWith('fr');const id=selectedOrganization(keycloak.tokenParsed?.sub);const applies=!!id&&id!=='independent'&&!auth.hasAnyRole('PLATFORM_ADMIN')&&operational.includes(location.pathname.split('/')[1]??'');const query=useQuery({queryKey:[...licensingKeys.organization(id??''),'entitlements'],queryFn:()=>getEntitlements(id!),enabled:applies,refetchInterval:60000});if(!applies)return children;if(query.isPending)return <p>{fr?'Vérification de la licence…':'Checking license…'}</p>;if(query.isError)return <ApiErrorMessage error={query.error}/>;if(query.data.active)return children;return <section><p role="alert">{fr?'La licence de cet établissement est inactive. Les données restent consultables en lecture seule. Contactez votre administrateur pour renouveler l’abonnement.':'This organization’s license is inactive. Data remains available read only. Contact your administrator to renew the subscription.'}</p><Link to={`/organizations/${id}/license`}>{fr?'Consulter la licence':'View license'}</Link><fieldset disabled className="license-readonly"><legend>{fr?'Lecture seule':'Read only'}</legend>{children}</fieldset></section>}
