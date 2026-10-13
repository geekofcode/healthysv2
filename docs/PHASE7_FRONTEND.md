# Phase 7 — Conservation et bascule : interface

## Suppression de données pour tous les profils

La route authentifiée autonome `/me/data-deletion` ne dépend ni du chargement de `/me`, ni d’une licence ou d’une équipe active. Elle reste accessible après anonymisation pour suivre les résultats. L’écran `/me` conserve également le panneau lorsque la lecture du profil échoue, et propose un lien de suivi permanent.

L’écran `/me` propose une demande de suppression à tous les utilisateurs disposant d’une fiche personnelle, sans condition d’abonnement ni de rôle professionnel. Le formulaire exige la saisie locale exacte de `SUPPRIMER` ; l’utilisateur voit avant confirmation que le traitement est irréversible, asynchrone et distinct de la suppression du compte Keycloak.

- `GET /api/v1/me/data-deletion` : demandes de la personne authentifiée seulement.
- `POST /api/v1/me/data-deletion` : header `Idempotency-Key`, sans body.
- Le même identifiant est conservé en cas de réponse réseau incertaine ; il change après une réponse confirmée.
- Une demande `QUEUED` ou `RUNNING` bloque une deuxième demande depuis le formulaire et déclenche un rafraîchissement toutes les cinq secondes.
- Les compteurs supprimés, anonymisés et conservés ainsi que les explications serveur sont affichés. `COMPLETED` signifie demande traitée, sans promettre que les dossiers partagés ont été supprimés.
- Si la liste ne peut être lue, le formulaire reste bloqué pour éviter une nouvelle demande aveugle.

## Bascule conditionnelle

`GET /api/v1/care/cutover` fournit `{mode,migrationValidated,journeyValidated,restorationValidated}`. Le backend décide et impose la bascule. L’interface ne déduit jamais l’autorisation de bascule d’un simple paramètre local.

Les routes de consultation et l’ancien agenda passent par `LegacyCareBoundary`. Une erreur de contrôle ne montre pas les actions de l’ancien parcours. En modes `LEGACY` et `PILOT`, ces écrans restent accessibles conformément au contrôle serveur, pour éviter le retrait avant validation effective de la migration.

En mode `CARE_ONLY` :

- `/consultations/new` montre un lien vers le calendrier ; aucune création de consultation historique n’est proposée.
- `/consultations` et `/consultations/:id` conservent la lecture de l’historique avec les commandes d’écriture désactivées.
- `/agenda/legacy` devient un historique en lecture seule : aucun report, changement de statut, annulation ou création.
- L’agenda principal des équipes reste le calendrier et la file des visites du nouveau parcours.
- En `PILOT` également, l’agenda patient permet de répéter le nouveau parcours sans désactiver les écritures historiques avant validation.
- L’agenda principal patient utilise `GET /api/v1/care/visits/me` et affiche les visites programmées et de suivi. Aucun accès à l’espace clinique médecin ni commande de programmation patient n’est ajouté. Le patient conserve un lien vers l’historique ancien.

Le retrait physique des composants historiques n’est pas effectué avant la validation de migration : cette lecture demeure nécessaire pour la traçabilité et le retour arrière. Les commandes d’émission/annulation des factures historiques sont également masquées après `CARE_ONLY`, et restent bloquées en attendant le contrôle de bascule. Les encaissements/restitutions cash des factures du nouveau parcours demeurent disponibles sous leurs contrôles caisse et licence habituels. Les refus backend constituent la protection déterminante contre les appels directs.

## Validation

Tests dédiés : confirmation de suppression, clé stable après erreur réseau, statut en cours, données conservées, erreur de lecture ; garde de bascule avant/après activation et erreur de contrôle ; agenda patient sans commande d’écriture ni liens cliniques médecin.

Exécuter `npm test`, `npm run typecheck`, `npm run build`. Les tests navigateur avec une vraie session, PostgreSQL et MinIO restent dans la validation d’intégration de bascule.

## Historique clinique utilisable sans IA

L’espace médecin `/care/visits/:id` affiche aussi `CarePatientHistory`, alimenté par `GET /api/v1/patients/{patientId}/care-history`. La liste contient les comptes rendus validés autorisés, y compris ceux des autres établissements et les importations historiques, avec établissement, médecin, date, révision, motif, observations, diagnostic, traitement et suivi. La visite actuelle est exclue pour éviter une répétition du rapport édité.

Cet historique est indépendant des droits IA et ne sollicite aucun fournisseur. Les rapports étrangers restent strictement en lecture seule : aucun lien d’édition vers une autre visite. Un refus de consultation de l’historique s’affiche localement sans bloquer la saisie manuelle du rapport courant. Les tests vérifient les champs, l’exclusion de la visite actuelle, l’absence de contrôles étrangers et l’isolation d’une erreur d’accès.
