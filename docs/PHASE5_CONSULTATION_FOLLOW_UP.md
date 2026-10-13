# Phase 5 — Consultation et suivi

## Rencontre et rapports

La page `/care/visits/{id}` regroupe le dossier autorisé, les constantes de cette visite, son rapport, ses prescriptions et documents. Le démarrage est réservé au médecin affecté, avec licence, adhésion, profil et affectation actifs. L'établissement est verrouillé avant la visite et l'épisode; les permissions sont revérifiées après verrouillage.

- `POST /api/v1/care/visits/{id}/start {version}` démarre une visite payée et programmée, avec droits cliniques effectifs. Elle ne peut commencer avant son jour programmé dans le fuseau de l'établissement. La première rencontre fixe une seule fois début et expiration de l'épisode; les suivantes les conservent.
- `GET /{id}/report` exige un véritable contexte clinique. Un rôle global DOCTOR ou NURSE, même cumulé avec PATIENT, ne permet pas de lire les brouillons.
- `PUT /{id}/report {version,reason,observations,diagnosis,treatment,followUp}` conserve un brouillon indépendant pour chaque visite. Une version périmée produit HTTP409.
- `POST /{id}/report/finalize {version}` exige un motif et un diagnostic enregistrés, ajoute une révision immuable et termine la visite.
- `POST /{id}/report/corrections {version,correctionReason}` ouvre une nouvelle révision avec motif de correction. Les versions validées précédentes et leurs auteurs/dates sont conservés.

Les dossiers et documents restent soumis à `PatientAccessService`. `CARE_IN_PROGRESS_ACCESS_DURATION` (PT4H) borne l'accès d'une rencontre encore en cours si la couverture de l'épisode expire pendant celle-ci. Cette exception ne prolonge pas l'épisode et ne permet pas de nouveau retour gratuit. Une correction après expiration nécessite un autre accès effectif, notamment une autorisation explicite du patient.

## Prescriptions et PDF

Sous `/api/v1/care/visits/{id}/prescription`:

- GET : brouillon pour le personnel clinique effectivement habilité, dernière révision validée pour le patient propriétaire.
- PUT : `{expectedVersion,items:[{medication,dosage,route,frequency,duration,instructions}],correctionReason?}`.
- POST `/finalize {expectedVersion}` : exige des lignes complètes et un rapport validé.
- POST `/corrections {expectedVersion,reason}` : ouvre un nouveau brouillon; les anciennes révisions demeurent immuables.
- GET `/revisions` : historique autorisé.

`GET /api/v1/care/visits/{id}/documents` retourne les métadonnées accessibles. `POST` avec `{type:"REPORT"}` ou `{type:"PRESCRIPTION"}` génère un PDF à partir des dernières données validées, jamais d'un brouillon. Une génération identique réutilise le document de ces révisions. Le PDF contient patient, médecin, établissement, références, révisions, dates et contenu validé; police Unicode et pagination sont embarquées.

Les objets MinIO ont des clés privées, liées au patient et à la visite. Le téléchargement passe par `GET /api/v1/documents/{documentId}/content`, avec contrôle d'accès à chaque requête. Aucun lien public MinIO n'est exposé. Le bucket de déploiement doit interdire l'accès anonyme; ses identifiants restent exclusivement backend. Les uploads sont nettoyés en cas d'échec ou de rollback. L'interface télécharge et imprime via des requêtes authentifiées.

## Retours couverts et constantes

`POST /api/v1/care/episodes/{id}/follow-ups` avec `{professionalId,scheduledAt,reason?}` et `Idempotency-Key` crée une visite distincte dans le même établissement. L'épisode doit être actif à la création et à la date choisie (échéance exclue). Affectation, spécialité, disponibilité et chevauchement du médecin sont vérifiés. Le médecin peut changer; le début, l'échéance et la facture de l'épisode restent identiques. Aucune écriture de facturation n'est effectuée pour une visite couverte.

`GET/PUT /api/v1/care/visits/{id}/intake` fournit les observations et constantes propres à la visite. Les infirmiers saisissent l'accueil avant démarrage; ensuite les données d'accueil ne sont plus écrasées. V38 reprend l'ancien accueil dans la première visite et maintient le point d'entrée historique pour cet accueil initial uniquement. Les retours suivants possèdent chacun leur propre relevé.

Les demandes répétées de création utilisent la même clé d'idempotence pour retrouver la même visite; changer sa charge utile avec cette clé produit un conflit. Les visites peuvent ensuite être déplacées avec les règles de la phase4 sans nouvelle facture.

## Caisse et validation

Les rapports par période/caissier/devise et clôtures sont décrits dans [PHASE5_CASH_REGISTER.md](PHASE5_CASH_REGISTER.md). Les clôtures conservent montants attendus, déclarations, écarts et liens immuables vers les reçus.

Validation locale: 260 tests backend hors intégration, dont génération et relecture de vrais PDF, sécurité des brouillons, versions, retours couverts et calculs de caisse; 121 tests frontend et build React réussis. La compilation Java21 et les frontières Modulith passent. Les migrations V35–V38 et les scénarios complets PostgreSQL/MinIO doivent encore être exécutés sur un environnement isolé; Docker et les services de test ne sont pas disponibles ici.
