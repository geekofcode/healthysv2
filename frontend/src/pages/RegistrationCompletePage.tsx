import {useEffect} from 'react';
import {useMutation, useQueryClient} from '@tanstack/react-query';
import {useNavigate} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {provisionPatient} from '../api/persons';
import {ApiErrorMessage} from '../components/ApiErrorMessage';

export function RegistrationCompletePage() {
  const {t} = useTranslation();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: provisionPatient,
    onSuccess: async () => {
      await queryClient.invalidateQueries({queryKey: ['person', 'me']});
      navigate('/me', {replace: true});
    },
  });

  useEffect(() => {
    if (mutation.isIdle) mutation.mutate();
  }, [mutation]);

  if (mutation.isError) {
    return <ApiErrorMessage error={mutation.error} />;
  }
  return <main className="centered">{t('registration.creatingProfile')}</main>;
}
