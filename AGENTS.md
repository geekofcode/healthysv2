## Délégation des tâches complexes
- Utiliser plusieurs agents lorsque la tâche contient des
  sous-tâches indépendantes et que l’outil le permet.
- Exemples : backend, frontend et revue indépendante.
- Pour une correction simple ou localisée, utiliser un seul agent.
- Chaque agent reçoit un objectif précis, un périmètre de fichiers
  et des critères de validation.
- Éviter que plusieurs agents modifient les mêmes fichiers.
- Transmettre uniquement le contexte nécessaire à chaque agent.
- L’agent principal assure l’intégration et la validation finale.
- Commencer avec 2 à 3 agents ; augmenter seulement si cela
  apporte un bénéfice concret.
- Éviter les fonctions complexes dans le code, privilégier plusieurs fonctions simples et claires.
## Périmètre produit actif
- Le socle courant est la consultation multi-établissement : licences et équipes,
  identité patient partagée et accès temporaires, accueil, épisodes, caisse cash,
  programmation et file médecin, rapports, prescriptions et documents, suivi,
  assistant IA optionnel selon abonnement, conservation et migration.
- Ne pas réintroduire téléconsultation, carnet mère-enfant, messagerie,
  exploitation laboratoire, stocks pharmacie ou dispensation dans les écrans,
  API, jobs et dépendances du produit sans nouvelle demande explicite.
- Les schémas et migrations historiques restent conservés pour la migration,
  le retour arrière et l'intégrité des dossiers ; ils ne constituent pas des
  modules opérationnels actifs.
- Ne retirer le parcours ancien consultation/rendez-vous qu'après les preuves
  de migration et de recette définies par la procédure de bascule Phase 7.
