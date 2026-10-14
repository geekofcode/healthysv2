# Inscription et validation professionnelle

Le parcours ci-dessous est implémenté pour `medecin`, `nurse` et `laboratoire`. Les rôles administratifs, financiers et d’établissement ne sont jamais attribués par cette inscription.

## Création et validation

| Parcours | Création | Activation |
| --- | --- | --- |
| Indépendant | Inscription libre-service | Vérification par HEALTH’YS |
| Établissement | Invitation ou demande de rattachement | Vérification professionnelle et acceptation de l’affiliation |
| Assisté | Création par un administrateur, puis invitation | Même procédure de vérification |

Créer un compte authentifiable ne suffit pas à ouvrir l’accès aux dossiers des patients. Le formulaire recueille une demande de profession, jamais un rôle privilégié directement assigné. Après vérification, le backend met à jour la qualification et les droits locaux dans PostgreSQL.

## Identité et affiliations

Une identité Keycloak et une fiche Person peuvent porter à la fois un profil patient et un profil professionnel. L’accès professionnel opérationnel passe par un profil d’établissement explicitement sélectionné. Les affiliations sont séparées et peuvent être multiples : établissement, service, fonction, dates, statut et droits locaux.

Le rôle `medecin` ou `nurse` indique une capacité professionnelle. Il ne donne pas un accès global aux dossiers : les autorisations doivent également vérifier l’affiliation active, l’affectation ou la relation de soins. La fin d’une affiliation retire les accès de cet établissement sans supprimer le compte ni les autres affiliations. Les administrateurs d’organisation ne peuvent gérer que leur périmètre et ne peuvent attribuer les rôles de plateforme.

## Demande professionnelle

HEALTH’YS collecte la profession, une spécialité facultative, le numéro professionnel, l’autorité de délivrance, le pays et un justificatif privé PDF/JPEG/PNG de 5 Mo maximum. Les états sont brouillon, en attente, validée, refusée et suspendue ; les décisions conservent auteur, date et motif dans l’audit. Les exigences documentaires restent à définir selon les professions et les pays pris en charge.

L’organisation propose l’affiliation par invitation ; le professionnel l’accepte avec son compte et un courriel vérifié correspondant. HEALTH’YS valide la qualité professionnelle. Une invitation d’établissement ne contourne pas cette vérification : le dossier doit être validé avant acceptation.

Keycloak conserve les identifiants et les mécanismes de connexion ; ses rôles ne sont pas une source d’autorisation métier. Les justificatifs, décisions, affiliations et données de santé restent dans HEALTH’YS.

## Entrées dans l’interface

- Créer un compte patient.
- Demander un accès professionnel.
- Inviter un professionnel depuis l’espace organisation.

Un professionnel déjà inscrit doit pouvoir accepter une invitation avec son compte existant, après confirmation de son identité. Ne pas fusionner automatiquement des comptes sur la seule base du courriel.

Les liens d’invitation expirent après sept jours. Leur secret est conservé sous forme de hash en base et transmis dans le fragment du lien, puis conservé temporairement dans la session du navigateur pendant la connexion. L’application fournit le lien à partager ; elle n’envoie pas de courriel d’invitation automatiquement. Pour une création assistée, l’administrateur peut créer le compte Keycloak puis accompagner le dépôt de la demande, ou inviter le professionnel depuis un établissement. Le mot de passe reste défini par son titulaire.

Chaque utilisateur sélectionne explicitement son profil. `X-Profile-ID` désigne sa source locale et `X-Organization-ID`, lorsqu’il est fourni, doit correspondre à ce profil. Aucun accès professionnel ou établissement n’est déduit des rôles du jeton. Le patient peut gérer ces autorisations depuis **Mon profil → Autorisations de soins**. Les opérations de laboratoire et de facturation restent liées à un établissement ; elles ne deviennent pas globales lorsqu’aucun établissement n’est sélectionné.

Renseigner les pays dans le référentiel partagé avant les premières demandes. L’écran de validation administrative permet d’ajouter un pays (code ISO2 et nom). Les langues FR/EN sont initialisées par la migration V19. Les migrations V20 et V21 ajoutent le parcours professionnel et les relations/consultations indépendantes.

## Autorisations locales

Keycloak authentifie le compte. Les profils disponibles, rôles et accès sont définis dans PostgreSQL : administration plateforme accordée localement, adhésions aux établissements, qualifications approuvées et permissions personnalisées du profil sélectionné.

L’approbation ou la suspension modifie les états professionnels locaux dans la transaction métier. Aucun rôle n’est attribué ou retiré dans Keycloak et aucune livraison de synchronisation des rôles n’est exécutée. Les anciens rôles d’un jeton n’accordent pas de droits supplémentaires. La table historique `professional.role_sync_outbox` reste conservée pour la compatibilité des migrations.

Le client technique Keycloak est utile uniquement si la création assistée de comptes est activée (`STAFF_ACCOUNT_PROVISIONING_ENABLED`) ; il ne gère pas les droits HEALTH’YS.

## Justificatifs et référentiels

Le candidat saisit une spécialité libre avec suggestions des spécialités actives existantes. Une nouvelle valeur est intégrée au catalogue seulement à l’approbation administrative. La profession d’inscription reste l’un des rôles cliniques pris en charge : `medecin`, `nurse`, `laboratoire`.

Administration → Référentiels d’inscription (`/admin/registration-options`) permet d’ajouter, modifier et supprimer les pays. Un pays référencé par une adresse, une licence ou un dossier est protégé par les clés étrangères et ne peut être supprimé. Cette page affiche aussi les trois professions ; leur définition est dans `RegistrationReferenceController` et les contraintes de `ProfessionalOnboardingDtos`, avec les qualifications locales. Ce ne sont pas les spécialités.

V23 ajoute le type de pièce (passeport, carte nationale, permis), l’expiration et les scans recto/verso. La licence à l’ordre utilise le justificatif professionnel existant. Les trois fichiers sont requis pour soumettre et approuver, y compris pour un passeport (page d’identité et autre face/page justificative). Chaque fichier est privé, PDF/JPEG/PNG, limité à 5 Mo et contrôlé par signature. La date doit être strictement postérieure à la date courante UTC plus trois mois calendaires. Changer les métadonnées d’identité invalide ses deux scans ; changer les titres professionnels invalide la licence.

V24 crée une notification interne durable pour les administrateurs à la soumission. Elle apparaît dans la cloche/centre de notifications lors du prochain chargement, y compris pour un administrateur qui se connecte plus tard. Aucun scan ni donnée d’identité n’est inclus dans la notification. Il n’y a pas d’envoi automatique de courriel pour cette notification. Les décisions se prennent dans Administration → Demandes professionnelles (`/admin/professional-requests`).

V25 ajoute le numéro de pièce d’identité (`varchar(100)`), obligatoire à la soumission et à l’approbation. Le brouillon peut rester incomplet. Modifier le numéro invalide les scans recto/verso, sans invalider la licence si ses métadonnées ne changent pas. Les dossiers historiques restent conservés.
