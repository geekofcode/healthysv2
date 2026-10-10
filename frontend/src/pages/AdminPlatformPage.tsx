import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';

export function AdminPlatformPage() {
 const {t,i18n}=useTranslation();const fr=i18n.language.startsWith('fr');
 const groups=[
  {title:fr?'Établissements et dossiers':'Facilities and records',description:fr?'Organiser la plateforme et consulter les personnes enregistrées.':'Organize the platform and browse registered people.',items:[
   {path:'/organizations',label:'nav.organizations',description:fr?'Établissements, services, départements et structures.':'Facilities, services, departments and structures.'},
   {path:'/professionals',label:'nav.professionals',description:fr?'Professionnels enregistrés, licences et affectations.':'Registered professionals, licences and assignments.'},
   {path:'/patients',label:'nav.patients',description:fr?'Fiches patients et dossiers autorisés.':'Patient profiles and authorized records.'}]},
  {title:fr?'Inscriptions et référentiels':'Registration and reference data',description:fr?'Vérifier les nouvelles demandes et configurer les choix proposés.':'Review applications and configure registration choices.',items:[
   {path:'/admin/professional-requests',label:'onboarding.reviewTitle',description:fr?'Consulter les justificatifs, approuver, refuser ou suspendre un professionnel.':'Inspect evidence, approve, reject or suspend a professional.'},
   {path:'/admin/registration-options',label:'onboarding.referenceTitle',description:fr?'Ajouter, modifier ou supprimer les pays et consulter les professions disponibles.':'Add, edit or delete countries and view available professions.'}]},
  {title:fr?'Finances et supervision':'Finance and oversight',description:fr?'Suivre la facturation et contrôler les accès à la plateforme.':'Track billing and inspect platform access.',items:[
   {path:'/billing',label:'nav.billing',description:fr?'Factures, paiements et suivi des soldes.':'Invoices, payments and outstanding balances.'},
   {path:'/admin/audit-security',label:'admin.openAudit',description:fr?'Journaux d’activité, accès aux données et événements de sécurité.':'Activity logs, data access and security events.'}]}];
 return <section><p className="eyebrow">{t('nav.admin')}</p><h1>{t('admin.title')}</h1><p>{fr?'Choisissez la tâche à effectuer. Chaque section regroupe les outils d’un même domaine.':'Choose a task. Each section groups tools for one area.'}</p>
 <div className="admin-sections">{groups.map(group=><section className="admin-section" key={group.title} aria-label={group.title}><header><h2>{group.title}</h2><p>{group.description}</p></header><div className="admin-destinations">{group.items.map(item=><Link className="admin-destination" to={item.path} key={item.path}><strong>{t(item.label)}</strong><p>{item.description}</p><span>{fr?'Ouvrir':'Open'} →</span></Link>)}</div></section>)}</div></section>;
}
