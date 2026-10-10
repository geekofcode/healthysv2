import {storedProfessionalInvitation,clearProfessionalInvitation} from '../auth/professionalInvitation';
import {useMutation,useQueryClient} from '@tanstack/react-query';
import {Link} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {acceptInvitation} from '../api/professionalOnboarding';
import {ApiErrorMessage} from '../components/ApiErrorMessage';
export function ProfessionalInvitationPage(){const {t}=useTranslation();const client=useQueryClient();const token=storedProfessionalInvitation();const accept=useMutation({mutationFn:()=>acceptInvitation(token),onSuccess:()=>{clearProfessionalInvitation();void client.invalidateQueries({queryKey:['professional-onboarding']});}});return <section><h1>{t('onboarding.acceptTitle')}</h1><p>{t('onboarding.acceptHint')}</p>{!token&&<p role="alert">{t('onboarding.invalidInvitation')}</p>}{accept.isSuccess?<p role="status">{t('onboarding.accepted')}</p>:<button disabled={!token||accept.isPending} onClick={()=>accept.mutate()}>{t('onboarding.accept')}</button>}{accept.isError&&<ApiErrorMessage error={accept.error}/>}<p><Link to="/professional-onboarding">{t('onboarding.title')}</Link></p></section>;}
