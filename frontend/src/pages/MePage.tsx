import {useQuery} from '@tanstack/react-query';

import {getMe} from '../api/persons';
import {ApiErrorMessage} from '../components/ApiErrorMessage';

export function MePage() {
  const query = useQuery({
    queryKey: ['person', 'me'],
    queryFn: getMe,
  });

  if (query.isPending) {
    return <p>Chargement de votre profil…</p>;
  }

  if (query.isError) {
    return <ApiErrorMessage error={query.error} />;
  }

  const person = query.data;
  return (
    <section>
      <p className="eyebrow">Mon profil</p>
      <h1>{person.firstName} {person.lastName}</h1>
      <dl className="profile-grid">
        <div>
          <dt>Numéro HEALTH'YS</dt>
          <dd>{person.personNumber}</dd>
        </div>
        <div>
          <dt>Statut</dt>
          <dd>{person.status}</dd>
        </div>
        <div>
          <dt>Contacts</dt>
          <dd>{person.contacts.length}</dd>
        </div>
        <div>
          <dt>Contacts d'urgence</dt>
          <dd>{person.emergencyContacts.length}</dd>
        </div>
      </dl>
    </section>
  );
}
