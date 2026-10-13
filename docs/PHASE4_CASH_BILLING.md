# Phase 4 — Facturation des soins en espèces

Les établissements définissent leurs tarifs et encaissent directement les soins. Aucun Stripe, carte ou mobile money n'est utilisé pour les patients. Stripe demeure réservé aux abonnements HEALTH'YS.

## Tarifs et facture

- `GET /api/v1/billing/care/tariffs?organizationId=UUID` : catalogue de l'établissement sélectionné.
- `POST /api/v1/billing/care/tariffs`, `PUT /tariffs/{id}` : administrateur de l'établissement, abonnement actif. Corps : `organizationId`, `code`, `description`, `currency`, `unitPrice`, `taxRate` (pourcentage de 0 à 100), `active` et `version` pour une modification. Conflit de version : HTTP 409.
- `POST /api/v1/billing/care/episodes/{episodeId}/invoice` : accueil/infirmier/administrateur, abonnement actif. Corps : `{ "items": [{ "tariffId": "UUID", "quantity": 1 }] }`, en-tête `Idempotency-Key` obligatoire.
- `GET /api/v1/billing/care/episodes/{episodeId}/invoice` : facture existante.

Une seule facture est admise par épisode. Les tarifs doivent être actifs et appartenir au même établissement ; toutes les lignes utilisent une devise commune. Les prix, descriptions et taxes sont copiés dans les lignes à la création. Le serveur calcule sous-total, taxes, total et solde. La facture est automatiquement émise, puis immuable. Une nouvelle demande avec une clé différente ou des lignes différentes renvoie un conflit ; la même demande retourne la facture existante.

## Encaissement et restitution

- `POST /api/v1/billing/care/invoices/{invoiceId}/cash` : `{ "amount": 40, "currency": "CAD" }`.
- `POST /api/v1/billing/care/invoices/{invoiceId}/refunds` : `{ "amount": 10, "currency": "CAD", "reason": "Correction" }`.
- `GET /api/v1/billing/care/invoices/{invoiceId}/receipts` : reçus immuables avec opérateur, date, numéro, type et montant.

Les deux écritures exigent un membre actif avec le rôle `CASHIER`, une licence active et une clé d'idempotence de 8 à 128 caractères. Un administrateur plateforme seul n'est pas caissier. Le rejeu exact d'une opération renvoie son reçu ; une clé réutilisée avec un autre montant, devise, motif ou type renvoie HTTP 409.

Les paiements partiels sont admis. Le solde doit atteindre zéro pour rendre l'épisode programmable. Le paiement ne commence jamais la durée de suivi et ne valide pas automatiquement le créneau médical préparé à l'accueil.

Les restitutions sont limitées au montant net encaissé et nécessitent un motif. Elles sont interdites après la première rencontre médicale pour cette phase. Avant cette rencontre, une restitution remet l'épisode en attente de paiement et invalide la programmation de ses visites initiales. Les encaissements précédents ne sont jamais supprimés. Les écritures de reçu sont protégées en base contre UPDATE/DELETE et disposent d'une rétention calculée depuis `DATA_RETENTION_YEARS` (30 ans par défaut).

Le navigateur imprime les factures et reçus à partir des données serveur. Les anciennes routes d'émission de factures avec prix libres et d'encaissement sans idempotence sont remplacées : elles renvoient HTTP 409. Les anciennes factures restent consultables ; les factures liées aux épisodes ne peuvent être émises ou annulées par les anciennes routes.

## Atomicité et validation

Les mutations verrouillent d'abord l'établissement, puis l'épisode et enfin la facture afin de partager l'ordre de verrouillage du calendrier. Facture, reçu, paiement, droits de programmation et audit sont mis à jour dans la même transaction. Les contraintes uniques protègent les doubles factures et reçus.

Les tests unitaires couvrent prix figés, taxes, paiement partiel/final, restitution, blocage après rencontre, changement de charge utile idempotente, rôle caissier effectif et limites monétaires. La validation PostgreSQL/Testcontainers reste nécessaire pour migrations et concurrence réelle.
