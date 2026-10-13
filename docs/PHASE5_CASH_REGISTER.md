# Phase 5.5 — Rapports et clôture de caisse

La caisse utilise exclusivement le journal d'encaissements et de restitutions en espèces de la phase 4. Les rapports et clôtures ne modifient aucune facture ni aucun reçu.

## Rapport

`GET /api/v1/billing/care/cash-report?from=ISOInstant&to=ISOInstant&cashierPersonId=UUID`

L'établissement est fourni par `X-Organization-ID`. Le début est inclus et la fin exclue : `[from,to)`. Cette règle permet de juxtaposer deux périodes sans compter deux fois un reçu situé à leur frontière. Les dates sont des instants UTC ; l'interface convertit les dates locales selon le fuseau de l'établissement.

La réponse contient `from`, `to`, `cashierPersonId`, `totals` et `cashiers`. Chaque total comporte `currency`, `payments`, `refunds`, `net` et `count`. Le net vaut les encaissements moins les restitutions. Chaque devise est séparée : aucun total artificiel ni conversion implicite n'additionne CAD, USD ou d'autres devises. Une période contenant uniquement des restitutions peut avoir un net négatif.

L'administrateur et le comptable consultent les opérations de l'établissement, avec un filtre facultatif par caissier. Le caissier consulte uniquement ses propres opérations. Les rôles d'établissement sont vérifiés en base ; un rôle global obsolète ne suffit pas. Les données historiques restent accessibles à un membre autorisé après expiration de l'abonnement.

## Clôture

`POST /api/v1/billing/care/cash-closures` avec un en-tête `Idempotency-Key` et le corps :

```json
{
  "from": "2026-10-01T00:00:00Z",
  "to": "2026-10-02T00:00:00Z",
  "cashierPersonId": "UUID",
  "declaredAmounts": [{ "currency": "CAD", "amount": 98.50 }],
  "note": "Comptage physique de fin de journée"
}
```

Le caissier clôture sa propre caisse ; l'administrateur peut clôturer celle d'un caissier de son établissement, y compris un caissier historique disposant de reçus. Le comptable dispose d'un accès en lecture, sans droit de clôture. Une licence active et l'identité de l'opérateur sont requises pour l'écriture.

Le serveur calcule, pour chaque devise, les paiements, restitutions, `expectedNet`, montant déclaré et `difference = declaredAmount - expectedNet`. Un écart est conservé, sans corriger les opérations sources. Chaque devise présente dans le journal doit être déclarée une fois, même si le montant compté est zéro. Une devise supplémentaire déclarée est conservée avec un attendu nul.

La période doit être terminée et sa fin ne peut être dans le futur. Une clôture chevauchant une clôture existante du même caissier et établissement est refusée (HTTP 409). Les périodes adjacentes sont permises. Un reçu ne peut appartenir qu'à une seule clôture.

L'établissement est verrouillé avant la lecture du journal et l'insertion de la clôture. L'encaissement emploie le même verrou et horodate ses écritures côté serveur ; une nouvelle opération ne peut être antidatée dans une période terminée. Les montants et liens vers les reçus sont enregistrés atomiquement dans la clôture. Le rejeu identique d'une clé retourne la clôture existante ; une autre période, déclaration ou note avec cette clé renvoie un conflit.

## Consultation et conservation

- `GET /api/v1/billing/care/cash-closures?cashierPersonId=UUID` : clôtures accessibles, des plus récentes aux plus anciennes.
- `GET /api/v1/billing/care/cash-closures/{id}` : montant attendu/compté, écart, opérateur, période et reçus associés pour audit ou réimpression.

Les clôtures et leurs liens sont immuables, protégés contre UPDATE/DELETE en base, et audités. Leur rétention est déterminée lors de la création par `DATA_RETENTION_YEARS` (30 ans par défaut).

Les tests unitaires couvrent regroupement par devise et caissier, net négatif, écarts, déclaration manquante ou dupliquée, frontières temporelles, période future, empreinte idempotente, lecture limitée au caissier, absence de droit d'écriture du comptable et blocage des périodes chevauchantes. La concurrence et les migrations doivent également être validées sur PostgreSQL.
