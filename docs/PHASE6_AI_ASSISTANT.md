# Phase 6 — Assistant IA selon abonnement

## Parcours livrés

L'assistant est disponible dans l'espace de visite du médecin assigné lorsque l'établissement possède une licence active incluant l'IA. Les fonctions manuelles de consultation, de saisie, de prescription et de validation ne dépendent pas du service Python.

| Carte | Comportement |
| --- | --- |
| 6.1 | Adaptateur Python privé OpenAI, contrats validés, jobs PostgreSQL persistants, idempotence, délais, tentatives bornées, reprise des leases expirées et métriques sans données cliniques. |
| 6.2 | Snapshot autorisé du motif, de l'accueil, des constantes, allergies, maladies, antécédents, alertes, rapports et prescriptions validés ; résumé avec références et données manquantes. Un changement des sources rend la synthèse obsolète. |
| 6.3 | Notes du médecin transformées en proposition structurée. Le médecin édite et copie explicitement la proposition dans son brouillon. Le job source est conservé dans le rapport et ses versions validées ; aucune validation automatique. |
| 6.4 | Enregistrement microphone ou import audio, transcription, correction du texte puis demande facultative de rédaction assistée. Audio privé temporaire MinIO, supprimé après traitement ou expiration, y compris après échec. |

Les réponses IA restent des propositions à vérifier. Les sources sont vérifiées contre le snapshot fourni ; le schéma et les citations ne garantissent pas l'exactitude des faits. Le prompt interdit d'inventer des diagnostics, traitements ou doses absents des notes.

## Architecture et droits

Le navigateur appelle uniquement Java. Java construit le snapshot après contrôle du dossier ; Python n'accède jamais à la base clinique. Le secret partagé et la clé OpenAI ne sont jamais exposés au frontend. Le service privé refuse les requêtes sans secret avant le décodage des données.

Chaque création et lecture vérifie le médecin réellement assigné, son profil actif, l'affectation et l'appartenance persistées à l'établissement, la licence et le droit IA, puis l'accès patient effectif. Le worker refait ces contrôles avant l'appel externe et avant la conservation du résultat, avec l'acteur persisté du job et sans JWT artificiel. La réaffectation, la révocation ou l'expiration peuvent annuler un job ou empêcher la lecture. Une requête déjà partie chez le fournisseur ne peut pas être rappelée.

Le contexte de dictée ne contient aucun dossier médical : seul l'audio est envoyé pour transcription. Aucun rapport brouillon d'une autre visite n'est incorporé aux résumés. Les données transmises sont bornées ; les omissions et troncatures sont signalées. Le hash des sources permet de détecter les modifications même dans les parties non envoyées.

## API Java

Base : `/api/v1/care/visits/{visitId}/ai` ; contexte `X-Organization-ID` habituel.

| Route | Contrat |
| --- | --- |
| `POST /jobs` | `Idempotency-Key`, corps `{kind: "SUMMARY"}` ou `{kind: "REPORT", notes: "..."}`. |
| `POST /dictation` | `Idempotency-Key`, multipart champ `audio`. |
| `GET /jobs` | Jusqu'à 50 jobs récents de l'acteur autorisé. |
| `GET /jobs/{jobId}` | État et résultat autorisé du job. |

Un job expose `id`, `kind`, `status`, `output`, `obsolete`, `errorCode`, `createdAt` et `model`. Les états sont `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED` et `CANCELLED`.

Les sorties structurées sont :

- Résumé : `{summary, sources: [{id, label}], missingData: []}`.
- Rapport : `{reason, observations, diagnosis, treatment, followUp}`.
- Transcription : `{text}`.

La sauvegarde manuelle du rapport accepte `aiJobId` facultatif. Un nouvel identifiant doit référencer une proposition de rapport réussie, autorisée, liée à la même visite et encore à jour. La provenance déjà enregistrée reste conservée pendant les modifications manuelles et dans la version validée immuable. Les contrôles habituels de version et d'accès clinique restent appliqués.

## Jobs, délais et rétention

La transaction de création conserve un snapshot immuable et un hash de la demande. Une même clé avec le même contenu retrouve son job ; un contenu différent provoque un conflit. Les jobs en attente par acteur sont bornés. Le worker réserve un job via `FOR UPDATE SKIP LOCKED`, conserve une lease avec jeton et effectue l'appel Python en dehors de la transaction clinique. Seul le détenteur de la lease courante peut publier le résultat. Les erreurs transitoires sont reprises avec délai et nombre maximal de tentatives ; les délais expirés et les leases abandonnées sont traités après redémarrage.

Le SDK Python ne fait pas de reprises cachées. Un timeout peut survenir après une facturation fournisseur : la déduplication applicative n'implique pas une exécution exactement une fois chez OpenAI.

Les snapshots, propositions et transcriptions portent une échéance de rétention à la création, selon `DATA_RETENTION_YEARS` (30 par défaut). L'audio temporaire suit une durée distincte, courte et configurable. Le worker purge l'objet MinIO après succès, échec, annulation ou échéance ; la purge reste active lorsque l'exécution IA est désactivée. Un balayage du préfixe dédié `ai-temporary/` élimine aussi les objets plus anciens que le TTL, y compris les orphelins après arrêt brutal avant commit. Il parcourt au maximum dix pages de cent objets par passage, avec continuation. Le compte MinIO doit disposer de `ListBucket` limité à ce préfixe, ainsi que `GetObject`, `PutObject` et `DeleteObject`. Configurer également une règle de cycle de vie sur ce préfixe comme filet de sécurité contre les arrêts prolongés.

## Configuration

Copier les valeurs de `.env.example`, puis configurer les secrets dans l'environnement de déploiement.

| Variable | Valeur par défaut / rôle |
| --- | --- |
| `HEALTHYS_AI_ENABLED` | `false`, activation des créations et de l'exécution. |
| `HEALTHYS_AI_SERVICE_URL` | `http://ai:8000`, accès privé Java → Python. |
| `HEALTHYS_AI_SERVICE_TOKEN` | Aucun défaut ; secret partagé d'au moins 32 caractères. |
| `OPENAI_API_KEY` | Clé uniquement dans Python. |
| `OPENAI_TEXT_MODEL` | `gpt-4.1-mini`, modèle configurable. |
| `OPENAI_TRANSCRIPTION_MODEL` | `gpt-4o-mini-transcribe`, modèle configurable. |
| `HEALTHYS_AI_TIMEOUT` | `PT60S`, délai complet de l'appel Java. |
| `AI_TIMEOUT_SECONDS` | `50`, délai Python, inférieur à celui de Java. |
| `HEALTHYS_AI_JOB_DEADLINE` | `PT15M`, échéance globale du job. |
| `HEALTHYS_AI_MAX_ATTEMPTS` | `3`. |
| `HEALTHYS_AI_POLL_INTERVAL_MS` | `5000`. |
| `HEALTHYS_AI_AUDIO_SWEEP_INTERVAL_MS` | `60000`, balayage des audios orphelins. |
| `HEALTHYS_AI_AUDIO_TTL` | `PT1H`, borne de conservation audio. |
| `AI_MAX_AUDIO_BYTES` | `10485760`, 10 Mio, commun Java/Python. |
| `AI_MAX_SNAPSHOT_BYTES` | `180000`, borne Python. |
| `AI_MAX_NOTES_CHARS` | `30000`. |
| `AI_MAX_OUTPUT_CHARS` | `50000`. |
| `AI_MAX_OUTPUT_TOKENS` | `6000`. |
| `AI_MAX_CONCURRENCY` | `4`, borne Python. |
| `SCHEDULING_POOL_SIZE` | `4`, partage des tâches planifiées Java. |

Déploiement Compose : `docker compose --profile ai up --build`. Aucun port Python n'est publié sur l'hôte ; seule la communication interne est prévue. Pour un backend hors Docker, définir une URL privée adaptée. La durée de capture navigateur est limitée à cinq minutes ; les imports et la taille effective restent contrôlés côté backend.

Ne pas activer de journalisation des corps, audio, prompts ou réponses dans le proxy, l'APM ou les clients HTTP. Le service Python propose `/health` et `/metrics` protégés par le même secret ; Java expose des métriques agrégées Micrometer. Les conditions du compte OpenAI doivent être configurées pour les données utilisées. `store=False` est utilisé pour Responses mais ne promet pas une rétention fournisseur nulle.

## Migrations et validation

- `V39` : jobs, inputs et résultats réussis immuables, indices de reprise et de purge.
- `V40` : provenance IA du brouillon et des versions validées.
- Tests Java : droits effectifs, contrat, provenance, idempotence, jobs et audio ; tests PostgreSQL via Testcontainers en CI.
- Tests React : visibilité par abonnement et médecin assigné, propositions, copie explicite, clés idempotentes, transcription et nettoyage microphone.
- Tests Python : authentification, limites, sorties et citations, délais et erreurs, utilisation du SDK via transport simulé. Aucun appel OpenAI facturable dans ces tests.

Avant activation, effectuer un essai de bout en bout avec données synthétiques : abonnement IA actif → résumé → dictée → correction → proposition → sauvegarde → validation humaine ; refaire le parcours avec révocation d'accès, expiration de licence et interruption du worker, puis vérifier la purge effective MinIO. Voir également [`ai-service/README.md`](../ai-service/README.md).

Validation locale finale : 294 tests Java sans intégration Docker, 135 tests frontend, 15 tests Python, compilation Java et build React réussis. Les tests PostgreSQL/Testcontainers et le parcours réel MinIO/OpenAI restent à exécuter dans un environnement configuré.
