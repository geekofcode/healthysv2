import {useQuery} from '@tanstack/react-query';

import {getMe} from '../api/persons';
import {ApiErrorMessage} from '../components/ApiErrorMessage';
import {useTranslation} from 'react-i18next';

export function MePage() {
  const {t} = useTranslation();
  const query = useQuery({
    queryKey: ['person', 'me'],
    queryFn: getMe,
  });

  if (query.isPending) {
    return <p>{t('profile.loading')}</p>;
  }

  if (query.isError) {
    return <ApiErrorMessage error={query.error} />;
  }

  const person = query.data;
  return (
    <section>
      <p className="eyebrow">{t('profile.eyebrow')}</p>
      <h1>{person.firstName} {person.lastName}</h1>
      <dl className="profile-grid">
        <div>
          <dt>{t('profile.number')}</dt>
          <dd>{person.personNumber}</dd>
        </div>
        <div>
          <dt>{t('profile.status')}</dt>
          <dd>{person.status}</dd>
        </div>
        <div>
          <dt>{t('profile.contacts')}</dt>
          <dd>{person.contacts.length}</dd>
        </div>
        <div>
          <dt>{t('profile.emergencyContacts')}</dt>
          <dd>{person.emergencyContacts.length}</dd>
        </div>
      </dl>
    </section>
  );
}
