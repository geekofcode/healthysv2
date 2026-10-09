# Inscription et validation professionnelle

Le parcours ci-dessous est implémenté pour `medecin`, `nurse` et `laboratoire`. Les rôles administratifs, financiers et d’établissement ne sont jamais attribués par cette inscription.

## Création et validation

| Parcours | Création | Activation |
| --- | --- | --- |
| Indépendant | Inscription libre-service | Vérification par HEALTH’YS |
| Établissement | Invitation ou demande de rattachement | Vérification professionnelle et acceptation de l’affiliation |
| Assisté | Création par un administrateur, puis invitation | Même procédure de vérification |

Créer un compte authentifiable ne suffit pas à ouvrir l’accès aux dossiers des patients. Le formulaire recueille une demande de profession, jamais un rôle privilégié directement assigné. Après vérification, le backend attribue le rôle métier correspondant dans Keycloak via une intégration administrative autorisée.

## Identité et affiliations

Une identité Keycloak et une fiche Person peuvent porter à la fois un profil patient et un profil professionnel. Le profil professionnel doit pouvoir exister sans hôpital, pour l’exercice indépendant. Les affiliations sont séparées et peuvent être multiples : établissement, service, fonction, dates, statut et droits locaux.

Le rôle `medecin` ou `nurse` indique une capacité professionnelle. Il ne donne pas un accès global aux dossiers : les autorisations doivent également vérifier l’affiliation active, l’affectation ou la relation de soins. La fin d’une affiliation retire les accès de cet établissement sans supprimer le compte ni les autres affiliations. Les administrateurs d’organisation ne peuvent gérer que leur périmètre et ne peuvent attribuer les rôles de plateforme.

## Demande professionnelle

HEALTH’YS collecte la profession, une spécialité facultative, le numéro professionnel, l’autorité de délivrance, le pays et un justificatif privé PDF/JPEG/PNG de 5 Mo maximum. Les états sont brouillon, en attente, validée, refusée et suspendue ; les décisions conservent auteur, date et motif dans l’audit. Les exigences documentaires restent à définir selon les professions et les pays pris en charge.

L’organisation propose l’affiliation par invitation ; le professionnel l’accepte avec son compte et un courriel vérifié correspondant. HEALTH’YS valide la qualité professionnelle. Une invitation d’établissement ne contourne pas cette vérification : le dossier doit être validé avant acceptation.

Keycloak conserve les identifiants, les mécanismes de connexion et les rôles effectivement accordés. Les justificatifs, décisions, affiliations et données de santé restent dans HEALTH’YS.

## Entrées dans l’interface

- Créer un compte patient.
- Demander un accès professionnel.
- Inviter un professionnel depuis l’espace organisation.

Un professionnel déjà inscrit doit pouvoir accepter une invitation avec son compte existant, après confirmation de son identité. Ne pas fusionner automatiquement des comptes sur la seule base du courriel.

Les liens d’invitation expirent après sept jours. Leur secret est conservé sous forme de hash en base et transmis dans le fragment du lien, puis conservé temporairement dans la session du navigateur pendant la connexion. L’application fournit le lien à partager ; elle n’envoie pas de courriel d’invitation automatiquement. Pour une création assistée, l’administrateur peut créer le compte Keycloak puis accompagner le dépôt de la demande, ou inviter le professionnel depuis un établissement. Le mot de passe reste défini par son titulaire.

Les professionnels sélectionnent leur contexte dans la sidebar. `X-Organization-ID` sélectionne un établissement avec affectation active ; `independent` sélectionne explicitement l’exercice indépendant. L’accès indépendant aux dossiers nécessite une relation personnelle active et un consentement nominatif au bon périmètre. Le patient peut gérer ces autorisations depuis **Mon profil → Autorisations de soins**. Les opérations de laboratoire et de facturation restent liées à un établissement ; elles ne deviennent pas globales lorsqu’aucun établissement n’est sélectionné.

Renseigner les pays dans le référentiel partagé avant les premières demandes. L’écran de validation administrative permet d’ajouter un pays (code ISO2 et nom). Les langues FR/EN sont initialisées par la migration V19. Les migrations V20 et V21 ajoutent le parcours professionnel et les relations/consultations indépendantes.

## Attribution des rôles Keycloak

L’approbation enregistre une livraison durable dans `professional.role_sync_outbox`, dans la même transaction que la décision. Le worker attribue uniquement `medecin`, `nurse` ou `laboratoire`. Une suspension enregistre le retrait du rôle. Les erreurs restent visibles (`PENDING`, `FAILED`, `SYNCED`) et sont réessayées toutes les 30 secondes au minimum ; aucun secret ni contenu de réponse Keycloak n’est journalisé dans l’état de synchronisation.

Créer un client confidentiel dédié dans le realm `healthys`, activer son service account et limiter ses permissions d’administration à la consultation des rôles et à la gestion des mappings des utilisateurs concernés. Utiliser les permissions fines Keycloak pour limiter les rôles administrables aux trois rôles cliniques. Ne jamais fournir un compte administrateur global au backend. Configurer `KEYCLOAK_ADMIN_SERVER_URL` (URL HTTPS de Keycloak), `KEYCLOAK_ADMIN_REALM`, `KEYCLOAK_ADMIN_CLIENT_ID` et `KEYCLOAK_ADMIN_CLIENT_SECRET`, puis `PROFESSIONAL_ROLE_SYNC_ENABLED=true`. Docker Compose transmet déjà ces variables au backend via `env_file: .env`.

Le worker est désactivé par défaut : une approbation demeure alors en attente de synchronisation et ne donne aucun rôle clinique. Après synchronisation, le professionnel doit renouveler son jeton ou se reconnecter. Le backend vérifie également l’état du dossier à chaque authentification : les rôles cliniques présents dans un ancien jeton sont immédiatement ignorés pour un dossier en attente, rejeté ou suspendu, même si Keycloak est indisponible. Les comptes professionnels historiques sans dossier restent compatibles avec les mappings administrés manuellement.
