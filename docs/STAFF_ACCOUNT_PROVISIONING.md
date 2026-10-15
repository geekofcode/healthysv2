# Comptes employés et administrateurs locaux

L'administrateur d'établissement peut rattacher une identité locale existante par son adresse e-mail exacte, ou créer une identité minimale (nom, prénom, e-mail). Cette opération ne crée ni dossier patient, ni profil clinique, ni rôle global Keycloak. Les rôles et droits métier restent attachés à l'établissement.

La création locale et l'entrée `identity.staff_account_outbox` participent à la même transaction que l'ajout d'équipe. L'adresse est normalisée ; un verrou transactionnel sérialise les créations simultanées. Plusieurs identités existantes avec la même adresse déclenchent un conflit explicite, sans rapprochement arbitraire. Une identité désactivée ne peut pas être réactivée par une invitation.

## Activation du provisionnement Keycloak

Le worker est désactivé par défaut. Pour l'activer :

```dotenv
STAFF_ACCOUNT_PROVISIONING_ENABLED=true
KEYCLOAK_ADMIN_SERVER_URL=https://keycloak.example.org
KEYCLOAK_ADMIN_REALM=HEALTHYS
KEYCLOAK_ADMIN_CLIENT_ID=healthys-admin-service
KEYCLOAK_ADMIN_CLIENT_SECRET=secret
# Facultatif : client web et URL de retour HTTPS autorisée dans ce client.
STAFF_ACCOUNT_WEB_CLIENT_ID=healthys-web
STAFF_ACCOUNT_REDIRECT_URI=https://healthys.example.org
```

Le client technique a besoin des permissions d'administration permettant création, lecture et mise à jour d'utilisateurs ainsi que l'envoi d'actions par e-mail. Le realm doit disposer d'une configuration SMTP opérationnelle. Aucun mot de passe provisoire n'est stocké ni retourné : Keycloak envoie les actions `VERIFY_EMAIL` et `UPDATE_PASSWORD` (lien valable 24 heures).

Le compte distant est initialement désactivé. Son identifiant UUID est lié durablement à l'identité locale, puis l'identité locale est verrouillée et contrôlée active avant activation et envoi de l'invitation. L'e-mail d'invitation peut être renvoyé après une interruption ; les essais ne créent pas plusieurs comptes distants.

## Reprise et rapprochement

Une reprise peut retrouver un compte distant uniquement si son e-mail exact et son attribut `healthysProvisioningPersonId` correspondent à l'opération locale. Un compte distant préexistant portant uniquement le même e-mail n'est jamais lié automatiquement : l'opération passe à `BLOCKED` avec le code `KEYCLOAK_ACCOUNT_REQUIRES_RECONCILIATION`. Le rapprochement demande une vérification opérateur de l'identité et de l'appartenance du compte ; ne pas copier l'attribut pour contourner cette vérification.

Les incidents techniques produisent le code générique `KEYCLOAK_PROVISIONING_FAILED`, sans secret, réponse HTTP ou donnée médicale dans l'erreur persistée. Le worker reprend au plus dix essais par entrée, avec bail de cinq minutes et délai minimal de trente secondes. Après correction, un opérateur peut remettre une entrée contrôlée à `PENDING` avec `attempts=0` et `lease_until=NULL`. Aucune telle opération externe n'est effectuée par l'implémentation ou ses tests.

## Vue plateforme

`GET /api/v1/platform/accounts?page=0&size=25` est réservé à l'administrateur plateforme. Il expose une liste d'identités de comptes : `personId`, `firstName`, `lastName`, `email`, `status`, `accountStatus`. Il n'expose pas les données patient, dates de naissance, contacts d'urgence ou affectations aux équipes.

## Réinitialisation des accès depuis l’administration plateforme

`POST /api/v1/platform/accounts/{personId}/reset-access` exige le profil administrateur plateforme actif et signé ainsi qu’une clé `Idempotency-Key`. L’opération crée une demande persistante ; elle ne supprime aucun profil ni donnée médicale. `GET .../reset-access/requests/{requestId}` fournit son état (`QUEUED`, `RUNNING`, `SENT`, `FAILED`).

Activer le worker avec `ACCOUNT_RESET_ENABLED=true` et régler `ACCOUNT_RESET_POLL_MS` (défaut 10000). Il réutilise le client technique `KEYCLOAK_ADMIN_CLIENT_ID/SECRET`, `KEYCLOAK_ADMIN_SERVER_URL`, `KEYCLOAK_ADMIN_REALM` et le client/retour web `STAFF_ACCOUNT_WEB_CLIENT_ID`, `STAFF_ACCOUNT_REDIRECT_URI`. Les URL de ce worker sont HTTPS. Keycloak envoie son email d’action `UPDATE_PASSWORD` avec le SMTP de son realm ; le SMTP Brevo des contrats applicatifs ne configure pas automatiquement celui de Keycloak. Aucun email réel n’est envoyé par les tests.
