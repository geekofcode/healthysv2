# Interface et profil HEALTH’YS

L’accueil public présente le service à gauche et les entrées de connexion et d’inscription à droite. Les boutons ouvrent Keycloak : HEALTH’YS ne collecte jamais le mot de passe dans son frontend.

L’interface authentifiée utilise une sidebar, un header compact et un fil d’Ariane. Le thème bleu possède des variantes claire, sombre et système. Pour un compte connecté, le thème et l’URL HTTPS facultative de la photo sont enregistrés dans `identity.person_preferences` (migration V22). Le navigateur conserve une préférence locale pour les pages publiques.

## Listes et formulaires

Les organisations, patients et professionnels utilisent des tableaux paginés, une recherche et des actions par ligne. Les organisations proposent aussi un filtre de statut appliqué avant pagination. La suppression demande une confirmation et affiche les erreurs de dépendances renvoyées par l’API. Les boutons suivent les droits métier ; le backend reste l’autorité. Les factures et documents gardent leurs opérations métier, sans proposer une suppression arbitraire de dossiers financiers ou médicaux.

Les formulaires regroupent les champs par section, montrent les libellés et les contraintes, et proposent les listes adaptées aux référentiels. Les types d’établissement proviennent de `catalog.organization_type` ; les pays et langues du profil proviennent de `shared.country` et `shared.language`. Les choix vides ne créent pas de références fictives.

## Répartition des données

Keycloak conserve les identifiants, le mot de passe, le courriel du compte, les rôles et les noms nécessaires à l’identité de connexion. La première connexion crée si nécessaire la fiche `identity.person` liée au `sub`, avec les noms et le courriel du compte. Les nouvelles connexions ne remplacent pas une fiche existante.

Le formulaire Mon profil écrit en base HEALTH’YS les noms métier, le sexe, la date de naissance, la langue, l’adresse et les contacts. Les codes personne et patient restent immuables. L’authentification utilise toujours le sujet Keycloak, jamais un rapprochement par courriel. Les contacts ajoutés par libre-service ne deviennent pas automatiquement vérifiés.

Les colonnes existantes des migrations V1 à V7 restent inchangées. Les validations des nouvelles entrées suivent les types et limites SQL documentés : UUID pour les références, DATE pour la naissance, longueurs VARCHAR pour les noms, adresses et coordonnées. V22 ajoute uniquement les préférences d’affichage, absentes du modèle initial.

Le profil du compte Keycloak peut différer du nom métier après une modification dans HEALTH’YS. L’interface affiche le nom métier. Aucune synchronisation inverse automatique n’écrase les noms du compte Keycloak.

## Déploiement

Reconstruire le frontend et le backend, puis appliquer V22 avec Flyway. Pour un realm existant, suivre `docs/keycloak-theme.md` pour retirer les anciens attributs et mappers démographiques. Ne pas réimporter le realm et ne pas supprimer les données déjà présentes en base.

Les entrées Agenda, Consultations, Laboratoire, Mère-enfant, Documents, Téléconsultations, Pharmacie et Facturation suivent maintenant une présentation par tableau et filtres. Les actions Nouveau/Créer ouvrent les formulaires selon les droits. Les consultations disposent d’une nouvelle liste serveur limitée par contexte d’exercice et consentement. Le filtre de statut de l’agenda est appliqué avant pagination. Les listes de stocks et téléconsultations sont paginées côté navigateur sur la collection renvoyée par leurs APIs. Le carnet enfant reste accessible par sélection de patient, l’API ne proposant pas de collection globale.

Administration est organisée en sections avec descriptions : établissements/dossiers, inscriptions/référentiels, finances/supervision. Le libellé manquant `patients.number` est corrigé en français et anglais.
