import {useTranslation} from 'react-i18next';
export function LanguageSwitch(){const {i18n}=useTranslation();return <select aria-label="Langue / Language" value={(i18n.resolvedLanguage??i18n.language??'en').startsWith('fr')?'fr':'en'} onChange={event=>void i18n.changeLanguage(event.target.value)}><option value="fr">Français</option><option value="en">English</option></select>;}
