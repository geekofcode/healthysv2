# Assistant privé HEALTH'YS — Phase 6

Adaptateur FastAPI réservé au backend Java, sans accès direct du navigateur, sans base
clinique et sans stockage de fichiers. Le backend reste responsable des jobs persistants,
des droits IA de l'abonnement, des accès au dossier, de l'audit, de la rétention et de la
validation humaine. Ce service ne finalise aucun rapport et ne prescrit rien.

## Exécution

Python 3.12+, `pip install -r requirements.lock.txt`, charger les variables de `.env.example`,
puis `uvicorn healthys_ai.app:create_app --factory --host 127.0.0.1 --port 8000 --no-access-log --log-level critical`.
Aucune valeur de secret par défaut. Une clé OpenAI et un secret partagé d'au moins 32
caractères sont obligatoires au démarrage. Aucun fichier `.env` n'est chargé automatiquement.
Le Dockerfile exécute le service sous un utilisateur non privilégié.

En production, utiliser uniquement un réseau privé entre Java et Python, ne pas publier le
port sur Internet, appliquer un chiffrement interservices si le réseau n'est pas de confiance,
et limiter la taille des requêtes également au reverse proxy. Pas de CORS ni d'interface API publique.

## Contrat

Toutes les routes, y compris `/health` et `/metrics`, exigent
`Authorization: Bearer <HEALTHYS_AI_SERVICE_TOKEN>`.

`POST /v1/generate` :

```json
{"jobId":"uuid-du-job","kind":"SUMMARY","snapshot":{"sources":[{"id":"report-uuid","label":"Rapport validé du 12/10/2026","data":{"observations":"..."}}]}}
```

- `SUMMARY` : `snapshot` fourni par Java contient exclusivement les données auxquelles
  le médecin a accès. `snapshot.sources` est une liste d'objets ayant `id` et `label`
  uniques/stables ainsi que les données correspondantes. Sortie :
  `{ "model": "...", "output": { "summary": "...", "sources": [{"id":"...","label":"..."}], "missingData": ["..."] } }`.
  Les métadonnées `sourceDigest` et `truncatedSources` sont acceptées dans le snapshot.
  Le prompt demande de signaler les sources tronquées comme données partielles.
  Le service rejette les citations inconnues, les libellés modifiés, les doublons, les
  références inline discordantes et une
  synthèse non vide sans citation lorsqu'il existe des sources. Le rattachement de chaque
  affirmation à son texte source reste à contrôler par le médecin : la validation de
  schéma ne garantit pas l'exactitude clinique.
- `REPORT` : ajoute `notes` non vide. Sortie `output` : `{reason, observations, diagnosis,
  treatment, followUp}`, tous des textes. Le prompt interdit de compléter un diagnostic
  ou un traitement absent ; les informations non fournies doivent rester vides.
- `TRANSCRIPTION` : ajoute `audioBase64` et `audioMimeType`. Formats acceptés :
  `audio/webm`, `audio/ogg`, `audio/wav`, `audio/x-wav`, `audio/mpeg`, `audio/mp4`.
  Sortie `output` : `{ "text": "..." }`, limitée à 30 000 caractères. L'audio est traité en mémoire puis les références
  locales sont libérées ; aucun fichier temporaire n'est écrit par Python. Java purge
  séparément sa copie temporaire MinIO, y compris après échec/expiration. La transcription
  et sa correction sont distinctes de la génération puis de la validation du rapport.

Les faits de la synthèse doivent porter des références inline `[sourceId]`.

Le modèle texte utilise `client.responses.parse(..., text_format=<Pydantic>, store=False)`.
La transcription utilise `client.audio.transcriptions.create(..., response_format="json")`.
Aucun outil externe, recherche web ou historique de conversation n'est activé. `store=False`
désactive le stockage de réponse Responses ; cela ne constitue pas une garantie de rétention
nulle de toutes les données chez le fournisseur (configurer les conditions du compte adaptées).

## Limites et reprises

Variables : `AI_TIMEOUT_SECONDS`, `AI_MAX_SNAPSHOT_BYTES`, `AI_MAX_AUDIO_BYTES`
(maximum 24 Mo), `AI_MAX_NOTES_CHARS`, `AI_MAX_OUTPUT_CHARS`, `AI_MAX_OUTPUT_TOKENS`,
`AI_MAX_CONCURRENCY`, `OPENAI_TEXT_MODEL`, `OPENAI_TRANSCRIPTION_MODEL`.
Les modèles par défaut sont configurables ; confirmer leur disponibilité dans le compte.

- `401 UNAUTHORIZED` : secret manquant/incorrect.
- `413` : entrée trop volumineuse, ne pas réessayer sans la réduire.
- `422` : contrat invalide, notes/audio manquants ou format incorrect.
- `502 INVALID_PROVIDER_OUTPUT` : refus, réponse tronquée, citations ou schéma invalides.
- `503 AI_BUSY / AI_PROVIDER_UNAVAILABLE` et `504 AI_TIMEOUT` : reprises bornées pilotées
  par le job Java. Le SDK OpenAI a `max_retries=0` pour éviter les reprises cachées.

Le `jobId` est transmis mais pas conservé côté Python. La déduplication durable appartient
au backend. Un délai dépassé peut avoir déjà été facturé par OpenAI ; aucune garantie
exactly-once du fournisseur n'est supposée. La consultation manuelle doit rester disponible.

## Observabilité et tests

Pas de journaux de corps, de notes, d'audio, de jetons, d'identifiant patient ou de messages
bruts d'exception fournisseur. Les erreurs de validation ne renvoient jamais l'entrée soumise.
`/metrics` retourne des compteurs agrégés par type de tâche et résultat, sans données patient.
Désactiver également les journaux de requête détaillés des proxies et outils APM.

`pip install -r requirements-dev.txt && pytest` exécute les tests sans clé réelle, sans coût
OpenAI et sans données patient. Un essai de bout en bout avec données synthétiques et compte
OpenAI configuré reste nécessaire avant déploiement. Les versions directes vérifiées sont figées ; `requirements.lock.txt` contient aussi
les dépendances transitives résolues pour Python 3.12 et est utilisé par Docker.

Références officielles consultées :
- [Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses)
- [SDK Python](https://github.com/openai/openai-python)
- [Transcription](https://developers.openai.com/api/reference/python/resources/audio/subresources/transcriptions/methods/create)
