# Phase 7 — Migration historique, preuve de restauration et bascule

Les migrations Flyway V1–V42 sont additives. V42 crée uniquement les registres d'import et de bascule : elle n'importe ni ne supprime aucune donnée clinique à son exécution. Le mode initial reste `LEGACY`. Le nouveau parcours cash fonctionne sans IA ; aucune configuration OpenAI n'est nécessaire pour sa recette.

## Outils et prérequis

Depuis la racine du dépôt :

```bash
python -m venv migration-tools/.venv
migration-tools/.venv/bin/pip install -r migration-tools/requirements.txt
python -m unittest discover -s migration-tools/tests -v
```

Les opérations réelles nécessitent PostgreSQL avec V42 appliquée, `pg_dump`/`pg_restore` de la version du serveur, un compte opérateur limité et un MinIO avec versionnement activé. Les variables suivantes sont injectées par le gestionnaire de secrets, jamais inscrites dans les commandes, rapports ou dépôt :

- `MIGRATION_DATABASE_URL` : base source.
- `DATA_RETENTION_YEARS` : 30 par défaut, 1 à 200 ; échéance attribuée à partir de la création historique, sans prolonger artificiellement la conservation.
- `CARE_FOLLOW_UP_DURATION` : durée ISO 8601 du socle, `PT168H` (604 800 secondes, 7 jours) par défaut ; accepte jours/heures/minutes/secondes entiers. `CARE_FOLLOW_UP_SECONDS`, si présent, constitue un override explicite prioritaire pour cet import. Les épisodes historiques terminés restent `CLOSED`, même si une échéance calculée est dans le futur ; aucune gratuité rétroactive n'est ouverte.
- `BACKUP_S3_ENDPOINT`, `BACKUP_S3_ACCESS_KEY`, `BACKUP_S3_SECRET_KEY`, `BACKUP_S3_BUCKET`.
- `RESTORE_DATABASE_URL`, `RESTORE_S3_BUCKET` : cibles isolées vides, différentes des sources.

Les plans, snapshots et sauvegardes contiennent des données médicales : fichiers privés 0600 et répertoire 0700, hors dépôt/CI. Les conserver sur un volume chiffré avec les mêmes contrôles d'accès et échéances que les données source. Aucun payload clinique n'est affiché dans stdout. Les erreurs CLI produisent seulement un code générique sans traceback, SQL, DSN ni contenu d'exception. Les stdout/stderr des utilitaires PostgreSQL sont capturés et ne sont pas retransmis. Les investigations détaillées se font dans une session opérateur contrôlée, jamais avec des logs CI contenant les plans ou secrets.

## 1. Import à blanc

```bash
migration-tools/.venv/bin/python migration-tools/healthys_migrate.py dry-run --plan migration-tools/migration-plan.json
```

Sans sous-commande, le CLI fait également un dry-run. Il ouvre une transaction PostgreSQL **read-only / repeatable-read**. Une fixture exportée peut être examinée hors ligne avec `--snapshot fichier-snapshot.json`. Le plan contient les données à insérer, les identifiants UUIDv5 stables, les nombres de lignes source/cible, les rapprochements et les anomalies. Le CLI affiche uniquement l'empreinte SHA256, les nombres de lignes et le nombre d'anomalies.

Transformation :

| Source | Cible et règles |
|---|---|
| Consultation `COMPLETED` avec date de fin | Un épisode `CLOSED`, une visite `COMPLETED`, un rapport validé et sa révision immuable. Aucun épisode actif historique n'est inventé. |
| Rendez-vous lié à cette consultation | La même visite ; identités patient, établissement et médecin doivent concorder. Plusieurs consultations pour le même rendez-vous sont mises en anomalie. |
| Rendez-vous sans consultation | Visite `PENDING_PAYMENT` ; seulement `AWAITING_SCHEDULING` si une facture du même encounter est exactement soldée et son ledger concorde. Jamais de visite directement `SCHEDULED` : les règles actuelles d'affectation/disponibilité doivent être réappliquées. |
| Notes et observations | Texte avec identifiant, date et auteur originaux. Le validateur provient de l'acteur de l'audit `CONSULTATION / COMPLETE`, jamais d'une supposition basée sur le médecin affecté. Un audit absent ou ambigu bloque l'import ; les autres auteurs ne sont pas effacés ni réattribués. |
| Diagnostics, traitement, suivi, constantes | Contenu historique conservé ; diagnostic absent reste absent. Une référence de catalogue sans description retrouvable est une anomalie. |
| Facture, paiements et transactions | Facture existante rattachée au nouvel épisode, sans modifier prix, taxes, soldes, auteurs ni reçus. Paiements électroniques anciens restent historiques ; **aucune opération cash ni transaction électronique nouvelle n'est créée**. |
| Documents | Métadonnées et objets MinIO inchangés, liens historiques conservés et lien `CARE_VISIT` ajouté. Patient et auteur contrôlés. |
| Prescriptions et demandes de laboratoire | Originaux conservés, rapprochement source consultation → visite consigné. Aucun médicament, auteur, date ou prescription réémis. Les lectures historiques restent disponibles ; ces lignes ne deviennent pas des prescriptions nouvellement validées. |

Une consultation encore ouverte, une identité manquante, un auteur manquant, une chronologie incohérente, une facture ambiguë ou une divergence du ledger bloque l'import. Corriger la source dans une procédure tracée puis refaire le dry-run ; il n'existe pas d'option pour ignorer les anomalies et annoncer une migration réussie.

## 2. Sauvegarde et restauration réellement vérifiée

Arrêter les écritures de l'application, du service IA et des workers de purge. Suspendre les accès d'écriture MinIO. Ne pas se limiter à une sauvegarde dont la restauration n'a jamais été testée.

```bash
migration-tools/.venv/bin/python migration-tools/backup_restore.py backup --directory migration-tools/backups/pre-cutover --writers-paused
migration-tools/.venv/bin/python migration-tools/backup_restore.py restore-check --directory migration-tools/backups/pre-cutover --isolated-target-confirmed
```

La sauvegarde PostgreSQL et son manifeste utilisent le **même snapshot exporté**. Le manifeste contient le nombre de lignes et l'empreinte canonique de chaque table. MinIO : toutes les versions d'objets et les delete markers sont sauvegardés avec la clé, l'identifiant de version, l'état latest, la taille et SHA256 des octets. La conservation des credentials dépend du gestionnaire de secrets existant ; ils ne font pas partie de la sauvegarde.

`restore-check` refuse une base ou un bucket non vide, ou les mêmes cibles que la source. Il exécute réellement `pg_restore --single-transaction`, compare toutes les tables, restaure les versions dans le bucket isolé, relit les **octets** de chaque version puis vérifie la vue latest, y compris les suppressions. Les identifiants de version S3 changent dans la copie ; `restoration-proof.json` conserve la correspondance source → copie. Aucune source n'est modifiée. Le script ne crée, ne nettoie et ne détruit aucune base ou bucket automatiquement.

Une restauration interrompue doit être recommencée sur de nouvelles cibles isolées vides. Le script n'efface pas une restauration partielle à la place de l'opérateur. Conserver les empreintes de `manifest.json` et `restoration-proof.json` comme preuves pour la bascule.

## 3. Application gardée de l'import

Refaire le dry-run pendant la suspension, examiner le plan et sa réconciliation financière. Utiliser exactement son empreinte :

```bash
migration-tools/.venv/bin/python migration-tools/healthys_migrate.py apply --plan migration-tools/migration-plan.json --expected-fingerprint EMPREINTE_SOURCE --writes-paused
```

L'import prend un advisory lock et des locks des tables source/cible, reconstruit le plan à partir de la base et compare la totalité au plan examiné. Toute modification source ou collision d'identifiant interrompt la transaction. Les lots de lignes sont traités dans **une transaction atomique par run** : pas de commit intermédiaire laissant un patient à moitié migré. Pour une base volumineuse, prévoir une fenêtre adaptée et tester la mémoire/temp disque sur une copie avant le pilote ; le CLI charge actuellement le snapshot en mémoire.

`care.migration_mapping` contient les identifiants, la provenance et l'empreinte des cibles réellement stockées. Une nouvelle exécution sur le même snapshot est idempotente, à condition que les cibles soient restées identiques. Le calcul du snapshot source exclut uniquement les nouveaux liens documentaires identifiés comme cibles dans `care.migration_mapping`, car ils sont produits par l'import dans la même table que les liens historiques. Les liens source originaux restent inclus ; un nouveau lien ajouté par un utilisateur n'est pas exclu et invalide correctement l'empreinte. Les anciennes lignes ne sont ni supprimées ni réécrites. Tous les rapports consolidés historiques et leurs révisions portent `patient_visible=false` : le contenu d'une note/observation privée ne devient pas visible au patient simplement parce qu'il a été importé. Les vues historiques continuent de respecter les indicateurs de publication source. Les documents PDF générés à partir de ces rapports doivent conserver cette confidentialité. La rétention configurée s'applique aux registres d'import ; ceux-ci restent des données sensibles.

## 4. Recette, vérification et activation

Faire d'abord la recette **cash et offre sans IA** sur une copie restaurée : accueil, facture, paiement, programmation, changement de médecin/report, consultation, suivi gratuit, expiration, isolation établissement, quotas, accès et clôture. Ajouter ensuite les vérifications des offres IA. Conserver les résultats de recette et de réconciliation sans informations médicales dans leurs logs.

Préparer un fichier de preuves, avec de vrais SHA256 des fichiers de résultats, et un opérateur nommé :

```json
{
  "journey": {"validated": true, "ticket": "PILOTE-RECETTE"},
  "restoration": {"validated": true, "ticket": "PILOTE-RESTAURATION"},
  "backup_manifest_sha256": "SHA256_64_HEXADECIMAUX",
  "restoration_test_sha256": "SHA256_64_HEXADECIMAUX",
  "cash_e2e_sha256": "SHA256_64_HEXADECIMAUX",
  "migration_review_sha256": "SHA256_64_HEXADECIMAUX"
}
```

```bash
migration-tools/.venv/bin/python migration-tools/healthys_migrate.py verify --run-id UUID_RUN --operator NOM_OPERATEUR --evidence preuves.json
```

La commande vérifie les sources, toutes les empreintes cible et l'absence d'anomalies avant de passer le run à `VERIFIED`. Les flags de preuve représentent la validation **signée par l'opérateur**, pas une exécution automatique des tests par cette commande. Le SHA256 lie cette attestation aux fichiers de résultats. La simple réussite d'un import ne valide ni la restauration ni le parcours métier.

L'activation reste une action d'administration plateforme via le mécanisme backend de bascule, avec ce run vérifié et ses preuves. Les modes sont `LEGACY`, `PILOT`, `CARE_ONLY`. Un flag d'environnement ne suffit pas. Tant que la validation n'existe pas, les anciennes routes ne sont pas retirées. Après activation, l'ancien historique reste lisible ; les anciennes écritures de parcours sont désactivées et le nouveau parcours devient la seule logique active.

## 5. Retour arrière

**Avant toute nouvelle activité sur les lignes importées** : arrêter les writers, repasser le gate en `LEGACY` par la procédure d'administration approuvée, puis :

```bash
migration-tools/.venv/bin/python migration-tools/healthys_migrate.py rollback --run-id UUID_RUN --writes-paused --operator NOM_OPERATEUR
```

Le CLI compare chaque empreinte cible. Toute modification de rapport/visite/facture importée, nouveau document clinique ou dépendance FK créée depuis l'import bloque le retour arrière. Les liens ajoutés par le run sont retirés, puis révisions/rapports/facture de raccordement/visites/épisodes ; les originaux, factures et paiements sont conservés. Toute erreur annule l'ensemble de la transaction. Un run rollbacké reste tracé et ne peut être réappliqué sous la même empreinte sans nouvelle revue.

**Après nouvelle activité** : ne pas supprimer les nouvelles données pour forcer le rollback. Arrêter le pilote et privilégier une correction en avant ou une restauration complète de PostgreSQL **et des objets/version MinIO du même manifeste**, sur cibles isolées, avec rapprochement des opérations survenues depuis la sauvegarde. Une restauration de la seule base peut rompre les documents. Faire valider la restauration et la reprise avant de rediriger les services ; aucun script fourni ne remplace automatiquement les sources de production.

## Validation disponible

Les tests Python couvrent la transformation, l'unicité consultation/rendez-vous, les auteurs et textes, les ledgers, les consultations ouvertes, les anomalies, la rétention, l'idempotence déterministe et les gardes opérateur. La vérification PostgreSQL/MinIO réelle dépend des services et utilitaires disponibles : les scripts l'exécutent explicitement, mais aucune restauration réelle n'est revendiquée sans son manifeste de preuve.
