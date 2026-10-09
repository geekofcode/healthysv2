import {Link, useLocation} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
const sections: Record<string,string> = {me:'nav.profile',organizations:'nav.organizations',professionals:'nav.professionals',patients:'nav.patients',agenda:'nav.agenda',consultations:'nav.consultations',laboratory:'nav.laboratory','maternal-child':'nav.maternalChild',documents:'nav.documents',chat:'nav.chat',teleconsultations:'nav.teleconsultations',pharmacy:'nav.pharmacy',billing:'nav.billing',admin:'nav.admin',notifications:'notifications.title','professional-onboarding':'onboarding.title','professional-invitation':'onboarding.invitationTitle'};
export function Breadcrumbs() {
  const {pathname} = useLocation();
  const {t,i18n} = useTranslation();
  const french = i18n?.language?.startsWith('fr');
  const parts = pathname.split('/').filter(Boolean);
  if (!parts.length) return null;
  const label = (part:string) => sections[part] ? t(sections[part]) : part === 'new' ? t('common.create') : part === 'edit' ? t('common.edit') : part === 'access' ? t('access.title') : part === 'orders' ? t('laboratory.title') : part === 'prescriptions' ? t('nav.pharmacy') : part === 'stocks' ? (french ? 'Stocks' : 'Stock') : part === 'audit-security' ? (french ? 'Audit et sécurité' : 'Audit and security') : part === 'professional-requests' ? (french ? 'Demandes professionnelles' : 'Professional requests') : part === 'invoices' ? (french ? 'Factures' : 'Invoices') : (french ? 'Détails' : 'Details');
  return <nav className="breadcrumbs" aria-label={french ? 'Fil d’Ariane' : 'Breadcrumb'}><ol><li><Link to="/">{t('nav.home')}</Link></li>{parts.map((part,index) => <li key={`${index}-${part}`}>{index === parts.length - 1 ? <span aria-current="page">{label(part)}</span> : ['laboratory','pharmacy','invoices','pregnancies','children'].includes(part) ? <span>{label(part)}</span> : <Link to={`/${parts.slice(0,index + 1).join('/')}`}>{label(part)}</Link>}</li>)}</ol></nav>;
}
