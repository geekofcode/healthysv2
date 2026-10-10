# Thème et inscription HEALTH’YS

Le thème `healthys` hérite du thème de connexion natif `keycloak.v2` (Keycloak 26.x). Il conserve les contrôles de sécurité, les messages de validation, les formulaires dynamiques et l’accessibilité natifs. Le bleu HEALTH’YS s’applique à la connexion, l’inscription, la récupération du mot de passe et la vérification du courriel. Les libellés sont disponibles en français et en anglais. Aucun template FreeMarker n’est dupliqué.

## Installer sur le realm existant

1. Sauvegarder le realm et exporter la configuration actuelle **Realm settings → User profile → JSON editor**. Sauvegarder aussi les client scopes avant modification.
2. Copier `keycloak/themes/healthys` dans `/opt/keycloak/themes/healthys`, ou monter le répertoire en lecture seule dans le conteneur Keycloak :

   ```yaml
   volumes:
     - ./keycloak/themes/healthys:/opt/keycloak/themes/healthys:ro
   ```

3. Redémarrer Keycloak si nécessaire pour vider le cache des thèmes. Dans **Realm settings → Themes**, sélectionner `healthys` comme **Login theme**. Dans **Localization**, activer FR et EN, et choisir FR par défaut. Définir **Display name** à `HEALTH’YS`.
4. Dans **User profile → JSON editor**, après sauvegarde, remplacer les anciens champs démographiques du formulaire par les champs de compte de `keycloak/healthys-user-profile.json` en conservant les attributs nécessaires aux autres intégrations. Les anciens champs date de naissance, sexe, adresse, téléphone et préférences ne doivent plus être requis ni proposés à l’inscription. Exporter leurs valeurs utiles avant de retirer leur définition ; ne pas supprimer les valeurs des comptes existants sans migration explicite. Le fichier définit explicitement `healthys_organization_id` en lecture/écriture administrateur uniquement ; ne jamais rendre cette affectation modifiable à l’inscription.
5. Conserver les scopes natifs `profile`, `email`, `roles` et le mapper d’audience API. Retirer le scope démographique `healthys-patient-profile` des clients HEALTH’YS s’il avait été installé.
6. Conserver **User registration**, **Login with email**, **Forgot password** et **Verify email**. Configurer un SMTP fonctionnel pour la vérification et la récupération.
   Dans le client web, vérifier également les URI de redirection et de déconnexion et les Web origins : `http://localhost:3000` pour Docker local, `http://localhost:5173` pour Vite, et les URL HTTPS exactes en production.
7. Le rôle métier par défaut doit être **`patient`**, sans aucun rôle privilégié. Vérifier le composite `default-roles-healthys` : aucune attribution de `admin`, `hopital`, etc. Les autres rôles et permissions existants restent inchangés.

**Ne pas réimporter `healthys-realm.json` pour mettre à jour un realm existant.** Cet export est prévu uniquement pour une installation neuve ; il ne constitue pas une procédure de migration. Les modifications ci-dessus sont ciblées et ne remplacent ni utilisateurs, ni clients, ni rôles.

Pour un realm neuf, `healthys-realm.json` contient les huit rôles métier minuscules et le thème ; installer le thème avant import puis appliquer `healthys-user-profile.json` via User Profile.

## Champs et limites

- Requis à l’inscription Keycloak : identifiant, courriel, prénom et nom. Le mot de passe reste géré par Keycloak.
- Tous les autres champs se complètent dans HEALTH’YS et sont sauvegardés en PostgreSQL.
- Les numéros personne/patient sont générés par HEALTH’YS et ne sont pas saisissables par l’utilisateur.
- Aucun claim démographique supplémentaire n’est nécessaire.

## Vérification avant mise en production

Sur un realm de test avec la même version Keycloak que la production :

1. Tester connexion, erreur de mot de passe, inscription, mot de passe oublié et vérification du courriel en FR et EN, au clavier et sur mobile.
2. Créer un patient, vérifier champs obligatoires et validation courriel. Vérifier que le rôle est `patient` uniquement.
3. Vérifier l’absence de claims démographiques dans un nouveau token sans le publier ni le journaliser. Vérifier l’absence de données cliniques et de rôle privilégié autoattribué.
4. Vérifier `/persons/me`, puis les fonctionnalités patient après provisionnement du dossier métier.
5. Tester les comptes existants, les comptes professionnels et les attributs d’organisation après fusion du profil.

Les fichiers JSON sont vérifiables localement avec `python -m json.tool`. Une vérification visuelle et fonctionnelle sur Keycloak reste nécessaire : elle ne peut pas être remplacée par un build frontend.

Références officielles : [Personnalisation des thèmes](https://www.keycloak.org/ui-customization/themes), [Profil utilisateur et mappers](https://www.keycloak.org/docs/latest/server_admin/).

## Architecture actuelle : authentification et profil métier séparés

La configuration précédente de champs patient dans Keycloak est remplacée. Keycloak conserve identifiant/courriel de connexion, mot de passe/MFA, rôles et nom complet. Ne plus demander date de naissance, sexe, adresse, téléphone ou préférences métier au formulaire Keycloak. `healthys-user-profile.json` définit désormais seulement les champs du compte. `healthys-patient-profile-scope.json` est une compatibilité vide ; retirer ce scope des clients HEALTH’YS et désactiver ses anciens mappers démographiques. Ne pas effacer brutalement les attributs d’utilisateurs existants : exporter et migrer les données utiles explicitement avant nettoyage. Le backend ignore ces anciens claims, et ne modifie jamais un profil métier existant lors de la connexion.

`GET /api/v1/persons/me` amorce uniquement une personne liée au `sub`, avec les noms/courriel du compte lors de la première connexion. Les données déjà en BD ne sont pas écrasées. `GET /api/v1/persons/me/profile` fournit la personne, son adresse principale et les listes de langues/pays ; `PUT /api/v1/persons/me` enregistre prénom/nom/autres prénoms, sexe, date de naissance (DATE), langue (UUID), coordonnées et contacts d’urgence conformément aux tables documentées. Le numéro personne, le numéro patient et le lien Keycloak sont immuables pour l’utilisateur. Une nouvelle coordonnée saisie en libre-service reste non vérifiée.

La migration V22 ajoute une extension explicitement distincte du modèle documenté : `identity.person_preferences`, liée à la personne, stocke le thème LIGHT/DARK/SYSTEM et une URL HTTPS de photo facultative. `GET/PUT /api/v1/persons/me/preferences` lit/enregistre ces paramètres en BD. Aucun champ de personnalisation n’est ajouté à Keycloak.

## Mise à jour visuelle du thème

Le fond reprend le dégradé de la page d’accueil (`#edf3ff` sur `#f5f7fb`), avec la même pile de polices Inter/system-ui, les boutons bleus, les cartes blanches et les champs compacts arrondis. Les formulaires et contrôles natifs Keycloak restent utilisés. Le thème de connexion reste clair ; la préférence privée du frontend ne traverse pas automatiquement les domaines.

Après copie du dossier de thème, redémarrer Keycloak pour renouveler son cache et recharger la page sans cache navigateur. Pour corriger un en-tête `healthys.identity` sur un realm existant, ouvrir **Realm settings → User profile → JSON editor**, retirer `"group": "identity"` des quatre attributs de compte et supprimer le groupe `identity` de `groups` (ou appliquer le fichier de profil actualisé après sauvegarde et fusion des attributs nécessaires). Le formulaire ne présente plus d’en-tête de groupe superflu. Mettre à jour le CSS seul ne modifie pas le profil du realm.
