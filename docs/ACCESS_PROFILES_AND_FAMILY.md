# Sélection explicite des profils et profils familiaux

Un compte authentifié ne reçoit aucun droit opérationnel avant le choix explicite d'un profil. `GET /api/v1/me/access-profiles` initialise uniquement l'identité propre au compte à partir des claims JWT et liste les profils accessibles. Le contexte interne `ACCOUNT` n'est pas un choix affiché : il sert aux opérations personnelles de création de profil et d'acceptation des contrats.

Toutes les requêtes opérationnelles sélectionnent `X-Profile-ID`. Les requêtes d'établissement fournissent aussi `X-Organization-ID` correspondant exactement à l'organisation du profil. Fournir une organisation sans profil ne suffit pas. Aucun rôle n'est cumulé entre les profils du même compte : un médecin qui possède également un profil administrateur doit choisir lequel il utilise.

Un profil plateforme est proposé uniquement à partir des rôles administrateur originaux du realm ou du client API configuré. Il doit aussi être choisi explicitement. Les profils hospitaliers sont accordés par les administrateurs via la gestion des équipes ; l'API personnelle ne permet pas de se créer un profil soignant ou hospitalier. Les profils patient personnels et familiaux sont créés via l'API suivante.

## Création

`POST /api/v1/me/patient-profiles`

```json
{
  "kind": "SELF",
  "firstName": "Prénom",
  "lastName": "Nom",
  "birthDate": "1990-01-01",
  "gender": "MALE"
}
```

Un profil `SELF` utilise la personne du compte authentifié. Il ne crée pas une seconde fiche patient si elle existe déjà. Une date de naissance connue n'est pas remplacée par une valeur différente via cette création.

Pour un enfant nouvellement enregistré :

```json
{
  "kind": "MINOR",
  "firstName": "Prénom enfant",
  "lastName": "Nom enfant",
  "birthDate": "2020-01-01",
  "guardianRelationship": "PARENT",
  "guardianBirthDate": "1990-01-01"
}
```

L'en-tête `Idempotency-Key` est obligatoire pour `MINOR` et doit être conservé lors d'une reprise de la même requête. Un même identifiant avec un contenu différent est refusé. `guardianRelationship` accepte `PARENT` ou `GUARDIAN`. La date du représentant est tirée de sa propre identité si elle est déjà connue ; autrement la valeur fournie est enregistrée comme déclaration personnelle. Fournir une date différente d'une date connue est refusé. Le représentant doit être majeur et le nouveau patient mineur.

Cette API ne permet pas de revendiquer un patient enfant existant : aucun identifiant de personne/patient n'est accepté. Une nouvelle personne enfant et sa fiche patient sont créées, sans compte Keycloak ni rôle global. La relation déclarée est enregistrée dans `family.guardianship` avec son échéance, sa trace d'attestation et son échéance de rétention. Une vérification externe de l'autorité parentale n'est pas simulée par cette déclaration.

## Identifiants et expiration

Le profil personnel a pour identifiant le UUID patient. Un profil familial a pour identifiant le UUID de relation et pour `sourceId` le UUID patient enfant ; `personId` reste celui du compte représentant. Les opérations doivent utiliser le patient du profil sélectionné, sans assimiler le dossier enfant au dossier personnel du représentant.

`PATIENT_ADULT_AGE=18` configure la limite d'âge. Le contrôle utilise la date de naissance actuelle et l'échéance enregistrée à la création : l'accès familial cesse au plus tard à cette échéance. Une relation révoquée, un représentant désactivé ou un enfant devenu majeur ne sont plus proposés comme profils familiaux. La disparition du profil familial ne supprime pas le dossier partagé de l'enfant.

La fiche est créée en base, puis l'acceptation du contrat du profil conditionne son usage opérationnel. Les signatures et documents contractuels sont gérés par le module de contrats. La création ou la découverte ne vaut pas acceptation.

## Préférences du compte

La langue et le thème se règlent dans `/me`, section personnalisation, et non dans les en-têtes. Les valeurs par défaut sont `SYSTEM` : thème selon `prefers-color-scheme`, langue selon les langues du navigateur (français/anglais, repli anglais). Le thème suit les changements du système et la langue suit l’événement `languagechange` quand le mode système est choisi. Les réglages sont propres au compte et partagés entre ses profils.

`GET /api/v1/persons/me/preferences` renvoie `{theme, avatarUrl, language}`. `PUT` accepte une mise à jour partielle ; changer la langue ne remplace ni le thème ni l’avatar, et changer le thème ne remplace pas la langue. `avatarUrl: null` efface la photo. Ces réglages personnels restent accessibles avant la signature du profil et ne donnent aucun accès aux opérations des établissements.
