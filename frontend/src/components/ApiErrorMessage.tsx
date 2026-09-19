import {ApiError} from '../api/client';
import {useTranslation} from 'react-i18next';

export function ApiErrorMessage({error}: {error: unknown}) {
  const {t} = useTranslation();
  if (error instanceof ApiError && error.status === 403) {
    return (
      <div className="alert alert-forbidden" role="alert">
        {t('errors.forbidden')}
      </div>
    );
  }

  const message = error instanceof Error
    ? error.message
    : t('errors.unexpected');

  return (
    <div className="alert" role="alert">
      {message}
    </div>
  );
}
