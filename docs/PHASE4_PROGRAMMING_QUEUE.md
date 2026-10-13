# Phase 4 — Programmation et file médecin

La facture et les encaissements sont décrits dans PHASE4_CASH_BILLING.md. Un épisode intégralement payé devient READY et sa visite AWAITING_SCHEDULING. Les coordonnées provisoires prises à l'accueil ne deviennent un rendez-vous effectif qu'après programmation validée.

## Visites

`GET /api/v1/care/visits/{id}` retourne l'identité de visite, sa version, sa durée de créneau et l'historique.

`PUT /api/v1/care/visits/{id}/schedule` reçoit `{professionalId, scheduledAt, version, reason}`. Seuls accueil, infirmier et administrateurs habilités peuvent programmer dans leur établissement avec une licence active. Les rôles sont revérifiés après acquisition du verrou. Une version périmée renvoie HTTP409 sans écraser une modification concurrente.

La programmation exige un médecin actif, membre de l'établissement, affecté à la date choisie et doté de la spécialité de l'épisode. Le créneau doit correspondre à un horaire récurrent (pas de créneaux alignés sur sa durée) ou à une disponibilité explicite. Une absence ou un intervalle bloqué prime sur une disponibilité positive. Les dates d'affectation et horaires récurrents utilisent le fuseau de l'établissement. Le médecin est verrouillé pour éviter deux réservations simultanées, y compris dans deux établissements; les anciens rendez-vous sont aussi pris en compte dans les chevauchements.

`CARE_VISIT_SLOT_MINUTES` définit la durée par défaut d'un créneau avec disponibilité explicite (30 minutes). Un horaire récurrent utilise sa propre durée. Une visite déjà programmée peut garder sa date et changer de médecin, ou être reportée à une date ultérieure; elle ne peut être avancée ni modifiée après démarrage. La visite, l'épisode et la facture gardent leurs identifiants. L'historique enregistre médecin/date/statut avant et après, auteur, date et motif. La file précédente est invalidée et les accès sont recalculés à partir du médecin et de la date effectifs.

## Calendrier et présence

`GET /api/v1/care/calendar?date=YYYY-MM-DD&professionalId=UUID` retourne `{professionalId,date,timezone,version,visits}`. Un médecin ne consulte que sa propre file; l'accueil choisit un médecin actif de l'établissement. Les bornes du jour utilisent le fuseau IANA, y compris les journées de 23 ou 25 heures. Les visites non payées ou non programmées sont exclues.

- `POST /api/v1/care/queue/presence {visitId,present,expectedVersion}`: présence par accueil/infirmier/administrateur.
- `POST /api/v1/care/queue/next {professionalId,date,expectedVersion}`: le médecin concerné appelle le premier patient PRESENT suivant l'ordre enregistré; sa présence devient CALLED. Ce geste ne démarre pas encore la consultation.
- `PUT /api/v1/care/queue/order {professionalId,date,visitIds,expectedVersion}`: permutation complète de la file par son médecin. Aucun identifiant étranger, doublon ou omission n'est accepté.

L'ordre initial est celui de la création des visites. Après une réorganisation, l'ordre personnalisé persiste et les nouveaux patients sont ajoutés à la suite. Déplacement, restitution, changement de présence, appel et réorganisation rendent les anciennes versions obsolètes. L'interface propose de recharger en cas de conflit.

## Validation

222 tests backend hors intégration et 115 tests frontend passent; compilation Java21, frontières Modulith et build React réussis. Les migrations V32–V34 et la concurrence sur PostgreSQL doivent encore être exécutées avec Docker/Testcontainers, indisponible ici. Aucun paiement électronique patient n'est introduit.
