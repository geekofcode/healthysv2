import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';

export function AdminPlatformPage() {
  const {t} = useTranslation();
  const destinations = [
    ['/organizations', 'nav.organizations'],
    ['/professionals', 'nav.professionals'],
    ['/patients', 'nav.patients'],
    ['/billing', 'nav.billing'],
    ['/admin/audit-security', 'admin.openAudit'],
  ];
  return <section>
    <p className="eyebrow">{t('nav.admin')}</p>
    <h1>{t('admin.title')}</h1>
    <p>{t('admin.managementDescription')}</p>
    <div className="card-grid">{destinations.map(([path, label]) =>
      <Link className="entity-card" to={path!} key={path}><strong>{t(label!)}</strong></Link>,
    )}</div>
  </section>;
}
