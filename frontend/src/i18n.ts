import i18n from 'i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import {initReactI18next} from 'react-i18next';

const resources = {
  en: {translation: {
    common: {loading: 'Loading…', save: 'Save', add: 'Add', none: 'None', cancel: 'Cancel', edit: 'Edit', create: 'Create', delete: 'Delete', actions: 'Actions', back: 'Back'},
    auth: {sessionInitializing: 'Initializing session…', keycloakInitializing: 'Initializing Keycloak…', securePortal: 'Secure portal', welcome: "Welcome to HEALTH'YS", intro: "Sign in with your HEALTH'YS account to access your health space.", login: 'Sign in', logout: 'Sign out'},
    registration: {createAccount: 'Create a patient account', creatingProfile: 'Creating your patient profile…'},
    nav: {main: 'Main navigation', home: 'Home', profile: 'My profile', organizations: 'Organizations', professionals: 'Professionals'},
    dashboard: {eyebrow: 'Dashboard', title: "Your HEALTH'YS space", description: 'The web application is connected to Keycloak and the Spring Boot API.'},
    profile: {eyebrow: 'My profile', loading: 'Loading your profile…', number: "HEALTH'YS number", status: 'Status', contacts: 'Contacts', emergencyContacts: 'Emergency contacts'},
    errors: {forbidden: 'Access denied: you do not have the required permissions.', unexpected: 'An unexpected error occurred.', sessionExpired: 'Your session is no longer valid. Please sign in again.', requestFailed: 'The request failed.'},
    organizations: {title: 'Organizations', new: 'New organization', loading: 'Loading organizations…', empty: 'No organization found.', number: 'Number', name: 'Name', legalName: 'Legal name', typeId: 'Organization type ID', phone: 'Phone', email: 'Email', website: 'Website', status: 'Status', details: 'Organization details', edit: 'Edit organization', create: 'Create organization', departments: 'Departments', services: 'Services', rooms: 'Rooms', beds: 'Beds', code: 'Code', description: 'Description', roomNumber: 'Room number', bedNumber: 'Bed number', roomType: 'Room type', addDepartment: 'Add department', addService: 'Add service', addRoom: 'Add room', addBed: 'Add bed', required: 'This field is required.', invalidEmail: 'Enter a valid email address.', invalidUrl: 'Enter a valid URL.', saved: 'Changes saved.'},
    professionals: {title:'Professionals',new:'New professional',create:'Create a professional',details:'Professional profile',search:'Search',searchPlaceholder:'Number, type or status',empty:'No professional found.',number:'Professional number',type:'Professional type',personId:'Person ID',status:'Status',licenses:'Licences',specialities:'Specialities',assignments:'Assignments',schedule:'Schedule',availabilities:'Availabilities',addLicense:'Add licence',addSpeciality:'Add speciality',addAssignment:'Add assignment',addSchedule:'Add schedule',addAvailability:'Add availability',primary:'Primary speciality',assignment:'Assignment',organizationId:'Organization',departmentId:'Department ID',serviceId:'Service ID',position:'Position',employeeNumber:'Employee number',startDate:'Start date',endDate:'End date',licenseNumber:'Licence number',issuingAuthority:'Issuing authority',issuedAt:'Issued on',expiresAt:'Expires on',specialityCatalogId:'Speciality',dayOfWeek:'Day',startTime:'Start time',endTime:'End time',slotDurationMinutes:'Slot duration (minutes)',startAt:'Start',endAt:'End',availabilityType:'Availability type'},
    weekdays:{1:'Monday',2:'Tuesday',3:'Wednesday',4:'Thursday',5:'Friday',6:'Saturday',7:'Sunday'},
    status: {ACTIVE:'Active', INACTIVE:'Inactive', AVAILABLE:'Available', OCCUPIED:'Occupied'}
  }},
  fr: {translation: {
    common: {loading: 'Chargement…', save: 'Enregistrer', add: 'Ajouter', none: 'Aucun', cancel: 'Annuler', edit: 'Modifier', create: 'Créer', delete: 'Supprimer', actions: 'Actions', back: 'Retour'},
    auth: {sessionInitializing: 'Initialisation de la session…', keycloakInitializing: 'Initialisation de Keycloak…', securePortal: 'Portail sécurisé', welcome: "Bienvenue sur HEALTH'YS", intro: "Connectez-vous avec votre compte HEALTH'YS pour accéder à votre espace santé.", login: 'Se connecter', logout: 'Déconnexion'},
    registration: {createAccount: 'Créer un compte patient', creatingProfile: 'Création de votre profil patient…'},
    nav: {main: 'Navigation principale', home: 'Accueil', profile: 'Mon profil', organizations: 'Organisations', professionals: 'Professionnels'},
    dashboard: {eyebrow: 'Tableau de bord', title: "Votre espace HEALTH'YS", description: "Le socle web est connecté à Keycloak et à l'API Spring Boot."},
    profile: {eyebrow: 'Mon profil', loading: 'Chargement de votre profil…', number: "Numéro HEALTH'YS", status: 'Statut', contacts: 'Contacts', emergencyContacts: "Contacts d'urgence"},
    errors: {forbidden: "Accès refusé : vous ne disposez pas des droits nécessaires.", unexpected: 'Une erreur inattendue est survenue.', sessionExpired: "Votre session n'est plus valide. Veuillez vous reconnecter.", requestFailed: 'La requête a échoué.'},
    organizations: {title: 'Organisations', new: 'Nouvelle organisation', loading: 'Chargement des organisations…', empty: 'Aucune organisation trouvée.', number: 'Numéro', name: 'Nom', legalName: 'Raison sociale', typeId: "ID du type d'organisation", phone: 'Téléphone', email: 'Courriel', website: 'Site web', status: 'Statut', details: "Fiche de l'organisation", edit: "Modifier l'organisation", create: 'Créer une organisation', departments: 'Départements', services: 'Services', rooms: 'Chambres', beds: 'Lits', code: 'Code', description: 'Description', roomNumber: 'Numéro de chambre', bedNumber: 'Numéro du lit', roomType: 'Type de chambre', addDepartment: 'Ajouter un département', addService: 'Ajouter un service', addRoom: 'Ajouter une chambre', addBed: 'Ajouter un lit', required: 'Ce champ est obligatoire.', invalidEmail: 'Saisissez un courriel valide.', invalidUrl: 'Saisissez une URL valide.', saved: 'Modifications enregistrées.'},
    professionals: {title:'Professionnels',new:'Nouveau professionnel',create:'Créer un professionnel',details:'Fiche professionnelle',search:'Rechercher',searchPlaceholder:'Numéro, type ou statut',empty:'Aucun professionnel trouvé.',number:'Numéro professionnel',type:'Type de professionnel',personId:'ID de la personne',status:'Statut',licenses:'Licences',specialities:'Spécialités',assignments:'Affectations',schedule:'Planning',availabilities:'Disponibilités',addLicense:'Ajouter une licence',addSpeciality:'Ajouter une spécialité',addAssignment:'Ajouter une affectation',addSchedule:'Ajouter une plage au planning',addAvailability:'Ajouter une disponibilité',primary:'Spécialité principale',assignment:'Affectation',organizationId:'Établissement',departmentId:'ID du département',serviceId:'ID du service',position:'Poste',employeeNumber:'Matricule employé',startDate:'Date de début',endDate:'Date de fin',licenseNumber:'Numéro de licence',issuingAuthority:'Autorité émettrice',issuedAt:'Date d’émission',expiresAt:'Date d’expiration',specialityCatalogId:'Spécialité',dayOfWeek:'Jour',startTime:'Heure de début',endTime:'Heure de fin',slotDurationMinutes:'Durée du créneau (minutes)',startAt:'Début',endAt:'Fin',availabilityType:'Type de disponibilité'},
    weekdays:{1:'Lundi',2:'Mardi',3:'Mercredi',4:'Jeudi',5:'Vendredi',6:'Samedi',7:'Dimanche'},
    status: {ACTIVE:'Actif', INACTIVE:'Inactif', AVAILABLE:'Disponible', OCCUPIED:'Occupé'}
  }},
} as const;

void i18n.use(LanguageDetector).use(initReactI18next).init({
  resources,
  supportedLngs: ['en', 'fr'],
  fallbackLng: 'en',
  interpolation: {escapeValue: false},
  detection: {order: ['navigator'], caches: []},
});

export default i18n;
