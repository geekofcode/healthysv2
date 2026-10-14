import {type ReactNode} from 'react';
import {useQuery} from '@tanstack/react-query';
import {Navigate,useLocation} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {useAuth} from './AuthContext';
import {getProfileContract} from '../api/profileContracts';
import {ApiErrorMessage} from '../components/ApiErrorMessage';
export function ProfileContractBoundary({children}:{children:ReactNode}){const auth=useAuth();const location=useLocation();const {i18n}=useTranslation();const fr=i18n.language.startsWith('fr');const id=auth.activeProfile?.id;const exempt=location.pathname==='/admin/contract-templates'&&auth.hasAnyRole('PLATFORM_ADMIN');const state=useQuery({queryKey:['profile-contract',id],queryFn:()=>getProfileContract(id!),enabled:!!id&&!exempt,staleTime:0,refetchInterval:30000});if(exempt)return children;if(!id)return <Navigate to="/profiles/select" replace/>;if(state.isPending)return <p>{fr?'Vérification du contrat…':'Checking contract…'}</p>;if(state.error)return <ApiErrorMessage error={state.error}/>;if(!state.data.signed)return <Navigate to={`/profiles/${id}/contract`} replace/>;return children;}
