# Rôles Keycloak et identité locale

Le realm déployé reste la source des rôles. Une conversion explicite à la frontière
backend conserve les politiques métier existantes ; le frontend utilise la même table.

| Keycloak | Rôle métier interne |
| --- | --- |
| admin | PLATFORM_ADMIN |
| comptable | ACCOUNTANT |
| gestionnaire | HOSPITAL_VIEWER |
| hopital | HOSPITAL_ADMIN |
| laboratoire | LAB_TECHNICIAN |
| medecin | DOCTOR |
| nurse | NURSE |
| patient | PATIENT |

Les rôles techniques et inconnus ne donnent aucun droit métier. Le backend accepte
également les anciens rôles internes explicites pour les déploiements existants.
Aucun rôle fourni n'est converti en PHARMACIST, CASHIER ou HOSPITAL_AGENT.
Les politiques cliniques existantes, y compris celles de l'admin, sont conservées.

## Gestionnaire

Lecture uniquement des organisations et professionnels. Aucun accès aux routes de
création/modification ni aux mutations API. Le token doit contenir un UUID dans
`healthys_organization_id` (ou `organization_id`) ; sans ce contexte, les lectures
sont refusées. Les listes et détails sont limités à cette organisation. Les
affectations d'autres organisations ne sont pas retournées dans la fiche professionnel.

## Provisionnement Person

`GET /api/v1/persons/me` crée la fiche manquante à partir du sujet JWT vérifié.
Le premier appel aux notifications peut également la créer, même avant `/me`.
La fiche est liée exclusivement au `sub` Keycloak ; aucune liaison automatique
par courriel n'est effectuée. Un verrou transactionnel PostgreSQL par sujet évite
les doublons lors d'appels concurrents. La fiche est validée dans une transaction
séparée avant les lectures métier. Une fiche existante n'est pas écrasée.

Prénom, nom et courriel proviennent des claims disponibles. Les noms absents sont
marqués « À compléter ». Aucun Patient ni Professional n'est créé implicitement,
aucune donnée clinique n'est inventée. Les parcours existants d'inscription patient
ou de création/affectation professionnelle restent nécessaires.

Aucune modification du formulaire Keycloak n'est nécessaire. Fournir les claims
`given_name`, `family_name`, `email`, `email_verified` améliore le profil initial.

## Déploiement

Reconstruire backend et frontend, puis se déconnecter/reconnecter. L'audience du
token doit toujours inclure la valeur KEYCLOAK_AUDIENCE attendue par l'API.
Vérifier /persons/me (200), notifications/unread-count (200), les menus selon rôle,
et le refus des mutations pour gestionnaire. La création automatique ne réactive
pas une Person inactive.
