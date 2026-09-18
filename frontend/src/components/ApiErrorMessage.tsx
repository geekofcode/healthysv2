import {ApiError} from '../api/client';

export function ApiErrorMessage({error}: {error: unknown}) {
  if (error instanceof ApiError && error.status === 403) {
    return (
      <div className="alert alert-forbidden" role="alert">
        Accès refusé : vous ne disposez pas des droits nécessaires.
      </div>
    );
  }

  const message = error instanceof Error
    ? error.message
    : 'Une erreur inattendue est survenue.';

  return (
    <div className="alert" role="alert">
      {message}
    </div>
  );
}
