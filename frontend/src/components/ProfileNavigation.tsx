import {useContext} from 'react';
import {NavLink} from 'react-router-dom';
import {useTranslation} from 'react-i18next';
import {useAuth} from '../auth/AuthContext';
import {HospitalPermissionContext} from '../auth/HospitalPermissions';
import {profileNavigation} from '../auth/profileNavigation';
export function ProfileNavigation(){const auth=useAuth();const access=useContext(HospitalPermissionContext);const {t}=useTranslation();const type=auth.activeProfile?.type??auth.roles[0]??'';const items=profileNavigation(type,access,auth.activeProfile?.organizationId??undefined);return <nav id="main-navigation" aria-label={t('nav.main')}>{items.map(item=><NavLink key={item.path} to={item.path} end={item.path==='/'}>{t(`careFlow.nav.${item.label}`)}</NavLink>)}</nav>;}
