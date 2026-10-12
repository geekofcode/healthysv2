import {useTranslation} from 'react-i18next';
import {useAuth} from '../auth/AuthContext';
import {AdminDashboardPage} from './AdminDashboardPage';

export function DashboardPage() {
  const {t} = useTranslation();
  const auth = useAuth();
  if (auth.hasAnyRole('PLATFORM_ADMIN')) return <AdminDashboardPage />;
  return (
    <section>
      <p className="eyebrow">{t('dashboard.eyebrow')}</p>
      <h1>{t('dashboard.title')}</h1>
      <p>{t('dashboard.description')}</p>
    </section>
  );
}
