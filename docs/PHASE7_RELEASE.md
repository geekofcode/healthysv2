# Phase 7 — Conservation et bascule

## État livré

La conservation, les demandes de suppression, les outils de migration et le verrou de bascule sont implémentés. Le nouveau parcours cash fonctionne indépendamment de l’assistant IA. Les comptes rendus validés du dossier partagé sont accessibles en lecture seule selon les autorisations effectives, même sans IA. Les brouillons et les écritures restent dans leur établissement ; les rapports historiques importés ne deviennent pas automatiquement visibles au patient.

La configuration livrée conserve le mode `LEGACY`, désactive les workers de suppression/rétention et ne réalise aucune migration de production. L’ancien parcours n’est pas retiré avant les preuves de validation : après bascule, ses commandes sont bloquées côté API et base, ses écrans deviennent en lecture seule et ses données historiques restent consultables.

## Ordre de déploiement pilote

1. Déployer les migrations V41 à V43 et l’application, avec IA et workers de purge désactivés. Vérifier les paramètres de conservation (30 ans par défaut), les quotas et les offres sans IA.
2. Suspendre tous les producteurs de données, y compris les jobs IA et de purge, puis réaliser une sauvegarde PostgreSQL et MinIO versionné. Exécuter une restauration vers une base et un bucket isolés et vérifier données et fichiers. Ne jamais utiliser la production pour le test de restauration.
3. Exécuter l’import à blanc, examiner anomalies, auteurs, identité patient, pièces et rapprochements financiers. Corriger les anomalies avant tout import. Les paiements électroniques historiques restent historiques ; ils ne sont pas transformés en encaissements cash.
4. Appliquer le plan revu sous suspension des écritures. Contrôler empreintes et rapprochements. Exécuter les tests natifs de la CI pilote puis la recette accueil → cash → programmation → consultation → suivi sans IA, reports, changement de médecin, isolation, accès expirés et quotas.
5. Faire vérifier le lot avec les preuves SHA-256 `backup_manifest_sha256`, `restoration_test_sha256`, `cash_e2e_sha256` et `migration_review_sha256`. L’administrateur plateforme peut ensuite appeler `POST /api/v1/care/cutover/activate` avec `migrationRunId`. Le serveur exige un lot `VERIFIED`, sans anomalies, et les validations de parcours et restauration. Aucune activation automatique.
6. Après observation du pilote, activer séparément les workers de suppression/rétention et contrôler leurs journaux et jobs bloqués. Ajouter ensuite l’assistance IA uniquement aux offres qui l’incluent.

Les commandes exactes d’import, sauvegarde, vérification et retour arrière sont dans [PHASE7_MIGRATION.md](PHASE7_MIGRATION.md). Les règles de purge et leurs limites sont dans [PHASE7_RETENTION.md](PHASE7_RETENTION.md), les écrans dans [PHASE7_FRONTEND.md](PHASE7_FRONTEND.md).

## Retour arrière et suppression

Le rollback d’import exige l’arrêt des écritures et refuse les cibles modifiées ou enrichies depuis l’import. Il remet le mode `LEGACY` et retire uniquement les données importées, en conservant les sources historiques. Après activité clinique nouvelle, ne pas forcer ce rollback : utiliser une restauration revue ou une correction en avant pour préserver les soins nouveaux.

Une suppression de profil anonymise les données privées et conserve les éléments partagés nécessaires à l’intégrité des dossiers avec motifs traçables. Le compte local est désactivé ; même un JWT existant ne permet plus d’utiliser l’application. Le suivi de la demande reste accessible. La suppression du compte chez le fournisseur d’identité fait l’objet d’une action administrative séparée documentée ; elle n’est pas simulée par la suppression locale.

## Validation et limites

Les tests unitaires backend, frontend et Python sont exécutés localement. Les 43 migrations et les tests SQL d’import, réexécution et rollback sont également vérifiés sur PostgreSQL embarqué WebAssembly. Les scénarios d’intégration Spring/MockMvc sont compilés et ajoutés à une CI dédiée PostgreSQL natif : leur exécution native et la restauration PostgreSQL/MinIO réelle restent nécessaires avant activation pilote. Aucun résultat local n’est présenté comme preuve de restauration de production.
