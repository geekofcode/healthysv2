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
4. Dans **User profile → JSON editor**, fusionner les attributs/groupes de `keycloak/healthys-user-profile.json` avec la configuration sauvegardée. Ne pas supprimer les attributs déjà utilisés par les intégrations. Le fichier définit explicitement `healthys_organization_id` en lecture/écriture administrateur uniquement ; ne jamais rendre cette affectation modifiable à l’inscription.
5. Dans **Client scopes**, importer `keycloak/healthys-patient-profile-scope.json` comme nouveau scope. S’il existe déjà, modifier ses mappers sans créer de doublon. Associer ce scope comme **Default** à `healthys-web-apps` et `healthys-mobile-apps`. Conserver les scopes `profile`, `email`, `roles` et le mapper d’audience API existants.
6. Conserver **User registration**, **Login with email**, **Forgot password** et **Verify email**. Configurer un SMTP fonctionnel pour la vérification et la récupération.
   Dans le client web, vérifier également les URI de redirection et de déconnexion et les Web origins : `http://localhost:3000` pour Docker local, `http://localhost:5173` pour Vite, et les URL HTTPS exactes en production.
7. Le rôle métier par défaut doit être **`patient`**, sans aucun rôle privilégié. Vérifier le composite `default-roles-healthys` : aucune attribution de `admin`, `hopital`, etc. Les autres rôles et permissions existants restent inchangés.

**Ne pas réimporter `healthys-realm.json` pour mettre à jour un realm existant.** Cet export est prévu uniquement pour une installation neuve ; il ne constitue pas une procédure de migration. Les modifications ci-dessus sont ciblées et ne remplacent ni utilisateurs, ni clients, ni rôles.

Pour un realm neuf, `healthys-realm.json` contient les huit rôles métier minuscules, le thème et le scope de profil ; installer le thème avant import et appliquer ensuite `healthys-user-profile.json` via User Profile. Le profil utilisateur est configuré au niveau du realm : les champs requis peuvent donc également être demandés aux comptes professionnels existants lors d’une action Update Profile. Ajouter cette action progressivement après validation.

## Champs et limites

- Requis à l’inscription : identifiant, courriel, prénom, nom, date de naissance et langue préférée.
- Facultatifs : autres prénoms, genre, téléphone et adresse (adresse, complément, ville, province/région, code postal, pays).
- Le mot de passe et sa confirmation restent gérés par Keycloak et la politique de mots de passe du realm.
- Le numéro de personne/patient et le lien au `sub` sont générés par HEALTH’YS ; ils ne sont jamais saisis par l’utilisateur.
- Assurance, contacts d’urgence, groupe sanguin, antécédents et autres informations cliniques se complètent dans HEALTH’YS. Ne pas les stocker dans Keycloak ni les exposer dans les tokens.
- Le validateur `local-date` contrôle le format de date ; le backend doit également refuser les dates futures. Les champs HTML ne remplacent pas la validation serveur.

## Claims du scope

| Attribut Keycloak | Claim | Access token | UserInfo |
| --- | --- | --- | --- |
| `middleName` | `middle_name` | Oui | Oui |
| `birthDate` | `birthdate` (ISO `YYYY-MM-DD`) | Oui | Oui |
| `gender` | `gender` | Oui | Oui |
| `phoneNumber` | `phone_number` | Oui | Oui |
| `locale` | `locale` (`fr` / `en`) | Oui | Oui |
| `streetAddress` | `healthys_address_line1` | Oui | Oui |
| `addressLine2` | `healthys_address_line2` | Oui | Oui |
| `city` | `healthys_address_city` | Oui | Oui |
| `region` | `healthys_address_province` | Oui | Oui |
| `postalCode` | `healthys_address_postal_code` | Oui | Oui |
| `country` | `healthys_address_country` (ISO2 majuscules) | Oui | Oui |

Les claims `given_name`, `family_name` et `email` proviennent des scopes natifs `profile`/`email`. Le scope dédié transporte les champs d’adresse pour le provisionnement métier ; ne l’associer qu’aux clients HEALTH’YS et ne jamais journaliser les tokens. Le provisionnement d’une adresse nécessite au minimum la première ligne et la ville. Le code pays fourni doit correspondre à une entrée du référentiel `shared.country` ; la langue préférée doit correspondre au référentiel de langues partagé. Sans ces prérequis, compléter l’adresse dans HEALTH’YS. Un champ affiché à l’inscription ne garantit pas à lui seul sa synchronisation avec la base métier. Les modifications ultérieures du profil Keycloak ne doivent pas écraser silencieusement des données métier déjà complétées.

## Vérification avant mise en production

Sur un realm de test avec la même version Keycloak que la production :

1. Tester connexion, erreur de mot de passe, inscription, mot de passe oublié et vérification du courriel en FR et EN, au clavier et sur mobile.
2. Créer un patient, vérifier champs obligatoires et validation date/courriel. Vérifier que le rôle est `patient` uniquement.
3. Vérifier les claims supplémentaires dans un nouveau token sans le publier ni le journaliser. Vérifier l’absence de données cliniques et de rôle privilégié autoattribué.
4. Vérifier `/persons/me`, puis les fonctionnalités patient après provisionnement du dossier métier.
5. Tester les comptes existants, les comptes professionnels et les attributs d’organisation après fusion du profil.

Les fichiers JSON sont vérifiables localement avec `python -m json.tool`. Une vérification visuelle et fonctionnelle sur Keycloak reste nécessaire : elle ne peut pas être remplacée par un build frontend.

Références officielles : [Personnalisation des thèmes](https://www.keycloak.org/ui-customization/themes), [Profil utilisateur et mappers](https://www.keycloak.org/docs/latest/server_admin/).
