import {useTranslation} from 'react-i18next';

export function DashboardPage() {
  const {t} = useTranslation();
  return (
    <section>
      <p className="eyebrow">{t('dashboard.eyebrow')}</p>
      <h1>{t('dashboard.title')}</h1>
      <p>{t('dashboard.description')}</p>
    </section>
  );
}
