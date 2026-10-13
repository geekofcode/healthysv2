# Phase 3 — Patient et prise en charge

## Identité unique et accueil

`patient.patient` conserve une identité globale par `person_id`, indépendante des établissements. L'accueil recherche la plateforme par nom, numéro patient/personne, identifiant officiel ou contact. Une recherche de moins de deux caractères retourne une liste vide hors administration plateforme; les pages sont plafonnées à 100 résultats. Les noms et dates servent à vérifier les homonymes: aucune fusion automatique de personnes ou de dossiers médicaux.

Les contraintes empêchent deux fiches pour une personne et deux identifiants officiels identiques (type, valeur, émetteur normalisés; émetteur absent et vide équivalents). V31 refuse les collisions historiques: les résoudre avant migration, sans fusion silencieuse.

Endpoints identité (réponses camelCase):

- `GET /api/v1/patients/identities?query=...`: recherche globale d'identité administrative.
- `GET /api/v1/patients/person-identities?query=...`: recherche minimale de personnes avec éventuel patient existant.
- `GET /api/v1/patients/{id}/identity`: identité, coordonnées, identifiants; aucun antécédent ou groupe sanguin.
- `POST /api/v1/patients/identities`: `{personId, identifiers:[{type,value,issuer,countryId,expirationDate}]}`.
- `PUT /api/v1/patients/{id}/identity`: `{firstName,lastName,middleName,gender,birthDate,email,phone}`. Aucun changement de lien Keycloak ni affirmation de vérification de contact; prénom intermédiaire omis conservé.

Lectures: accueil, infirmiers, médecins et administration, avec établissement et licence effective (administration plateforme peut rechercher globalement). Écritures: accueil/infirmiers/administration. Les recherches et lectures sont journalisées. L'ancienne liste `/patients` utilise aussi la recherche d'identité; les dossiers cliniques `/patients/{id}` restent soumis aux autorisations. Les rattachements administratifs ne peuvent être modifiés que dans leur établissement par le personnel autorisé, ou par l'administration plateforme.

## Accès temporaire au dossier et aux documents

Toutes les décisions de lecture/écriture clinique passent par `PatientAccessService` et sont journalisées. Ni l'administrateur plateforme ni l'administrateur local n'obtient un accès clinique automatique. Les demandes/grants sont liés au patient, à la personne et à l'établissement; avoir trouvé une identité ne suffit pas.

- Demande acceptée par le patient propriétaire: accès limité à 48 heures par défaut à partir de l'acceptation. Une demande en attente expire aussi après 48 heures.
- Visite programmée: épisode payé (`READY` ou `ACTIVE`), visite `SCHEDULED`, accès jusqu'à la visite plus la marge configurable; médecin affecté à cette visite ou infirmier actif de l'établissement.
- Épisode actif: accès jusqu'à son échéance, pour les infirmiers de l'établissement et les médecins de ses visites non annulées.
- Urgence: infirmier ou administrateur hospitalier habilité, licence/membership effectifs, justification obligatoire, durée d'une heure par défaut. Un médecin ne peut pas utiliser ce mécanisme d'urgence.
- Patient: contrôle et lecture de ses propres données indépendants d'une licence établissement. Le contrôle de consentement ignore un ancien contexte établissement seulement après vérification du propriétaire.

API sous `/api/v1/patients/{id}` (objets grants/demandes snake_case):

- `POST /access-requests {scope,reason}`, `GET /access-requests`; scope `FULL_RECORD`, `MEDICAL_RECORD` ou `DOCUMENT`.
- `POST /access-requests/{requestId}/accept` et `/revoke`: propriétaire seulement.
- `POST /emergency-access {reason}`.
- `GET /access-contexts`: acteurs et échéances des accès dérivés, visibles au propriétaire pour pouvoir les révoquer.
- `GET /access-grants`, `DELETE /access-grants/{grantId}`: révocation par le propriétaire.
- `POST /access-revocations {personId,organizationId}`: bloque aussi les accès dérivés des visites/épisodes pour cette personne dans cet établissement. Une nouvelle acceptation explicite lève ce blocage. Une urgence habilitée demeure une exception justifiée et journalisée.

La révocation d'un grant précis coupe ce grant; la révocation du contexte coupe aussi les droits dérivés. Les anciens consentements sans expiration ne sont plus valides; les nouveaux consentements sont réservés au propriétaire et plafonnés à la durée configurable. Les aliases document `DOCUMENTS` et `DOCUMENT` désignent le même scope.

## Épisode et accueil infirmier

- `GET /api/v1/care/episodes/reception-options`: médecins et spécialités éligibles dans l'établissement sélectionné.
- `POST /api/v1/care/episodes {patientId,reason,specialityId,professionalId,scheduledAt}`: crée un épisode `AWAITING_PAYMENT` et une première visite `PENDING_PAYMENT`. Médecin actif, affectation/membership et spécialité sont contrôlés; la date ne doit pas être passée.
- `GET /api/v1/care/episodes?patientId=...` et `/{id}`: épisodes de l'établissement, sans constantes. `?includeIntake=true` exige un accès clinique effectif.
- `GET /{id}/intake`, `PUT /{id}/intake {observations,vitals}`: accueil courant réservé aux infirmiers de cet établissement, sans ouverture de l'historique médical global. Constantes: `temperatureC`, `systolicBp`, `diastolicBp`, `heartRate`, `respiratoryRate`, `oxygenSaturation`, `weightKg`, `heightCm`.

L'accueil ne renseigne ni `startedAt` ni `expiresAt`: seule la première rencontre médicale démarre le suivi. La confirmation cash et la libération de la visite seront raccordées au parcours de caisse dans la phase suivante. Aucune commande permettant de contourner le paiement ou de démarrer une rencontre n'est exposée ici.

## Configuration

Variables backend, également chargées par Compose via `.env`:

| Variable | Défaut | Usage |
|---|---|---|
| `PATIENT_ACCESS_REQUEST_DURATION` | `PT48H` | Demande et autorisation acceptée |
| `PATIENT_ACCESS_EMERGENCY_DURATION` | `PT1H` | Autorisation d'urgence |
| `PATIENT_ACCESS_SCHEDULED_GRACE` | `PT1H` | Marge après heure prévue de la visite |
| `CARE_FOLLOW_UP_DURATION` | `PT168H` | Couverture copiée à la création de l'épisode |
| `DATA_RETENTION_YEARS` | `30` | Rétention des épisodes, visites et accueil |

## Validation

La compilation Java 21, 194 tests backend hors intégration (y compris frontières Modulith et permissions), 107 tests frontend et le build React passent localement. Les tests PostgreSQL/Testcontainers ajoutés couvrent notamment l'unicité normalisée, le même patient dans deux établissements et la structure de l'accueil. Leur exécution nécessite Docker, absent de cet environnement: la validation des migrations et du parcours complet sur une base isolée reste nécessaire avant déploiement.
