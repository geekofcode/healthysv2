# Contrats signés par profil

Chaque profil a son propre contrat : patient et enfant mineur (`PATIENT`), administration locale (`HOSPITAL`), personnel (`STAFF`), administration HEALTH’YS (`ADMIN`). Le choix du profil est obligatoire et ne cumule pas les droits d’un autre profil. La signature d’un profil n’autorise aucun autre profil. Un changement de type fonctionnel d’une appartenance, même si son identifiant reste inchangé, exige le contrat de ce nouveau type ; l’ancien accord reste consultable comme preuve historique.

L’administrateur plateforme crée un modèle en texte brut dans `/api/v1/admin/contract-templates`, fournit l’URL HTTPS réelle des conditions, leur version et le contenu contractuel, puis publie le modèle. Aucun texte juridique fictif ni URL de conditions n’est publié automatiquement. Sans modèle publié, le profil reste bloqué. Les seuls appels de configuration des modèles sont accessibles à l’administrateur sélectionné avant sa propre signature afin de permettre l’initialisation.

Les variables de modèle admises sont `{{signerName}}`, `{{profileLabel}}`, `{{organizationName}}`, `{{patientName}}`, `{{signedAt}}`, `{{signature}}`. La prévisualisation remplace les informations par les valeurs du serveur ; la date exacte est fixée lors de la signature et la signature manuscrite dispose d’une page réservée. Le contenu est du texte, sans HTML ni ressources distantes chargées. Une publication est immuable : une modification nécessite une nouvelle version. **Les signatures existantes restent valables après publication d’une nouvelle version** ; les nouveaux accords utilisent la version publiée courante. Il n’y a pas de demande rétroactive silencieuse d’acceptation.

## Acceptation et génération

`GET /api/v1/me/profile-contracts/{profileId}/template` fournit le modèle courant et `renderedBodyText`. `POST /api/v1/me/profile-contracts/{profileId}/sign` reçoit uniquement l’identifiant de modèle, `acceptTerms`, une signature PNG de canvas et `guardianAcknowledgement`. Le serveur vérifie la propriété du profil, le modèle publié, l’acceptation et une signature non blanche. Le PNG est limité, ses dimensions sont contrôlées avant décodage, puis il est réencodé sans métadonnées.

Le signataire est le compte authentifié, avec son nom en base et **l’adresse de son token Keycloak portant `email_verified=true`**. Aucun nom de signataire, destinataire, chemin de fichier ou acteur n’est accepté depuis le client. Pour le profil d’un mineur, le parent signe et doit confirmer sa qualité de représentant ; la relation parent/enfant et l’âge sont vérifiés par le résolveur de profils. L’identité de l’enfant est conservée dans le contenu signé sans remplacer le signataire.

Le contenu rendu, les conditions, leur version, le profil, l’horodatage, la signature et les empreintes SHA-256 sont conservés dans un accord immuable. Le worker durable génère le PDF et conserve PDF/signature dans le bucket MinIO privé sous un chemin généré par le serveur. `GET /.../{profileId}` fournit l’état ; `GET /.../{profileId}/pdf` télécharge la copie après contrôle de propriété, avec `Cache-Control: no-store`.

L’accord accepté autorise le profil dès la signature enregistrée ; un incident de génération ou de courriel ne bloque pas de nouveau le profil. Le PDF et le courriel sont repris séparément, avec réservation de job, bail, reprises bornées et erreurs génériques sans secrets. Les jobs IA vérifient également l’accord signé du profil d’origine avant exécution.

## Configuration de livraison

| Variable | Défaut | Usage |
|---|---|---|
| `PROFILE_CONTRACT_WORKER_ENABLED` | `true` | Génération PDF et traitement des jobs |
| `PROFILE_CONTRACT_POLL_MILLIS` | `30000` | Fréquence du worker |
| `EMAIL_ENABLED` | `false` | Activer explicitement la copie par courriel |
| `EMAIL_SMTP_HOST` | vide | Hôte SMTPS |
| `EMAIL_SMTP_PORT` | `465` | Port SMTPS |
| `EMAIL_SMTP_SSL` | `true` | TLS implicite, certificat et nom d’hôte vérifiés |
| `EMAIL_SMTP_USERNAME` | vide | Compte SMTP si requis |
| `EMAIL_SMTP_PASSWORD` | vide | Secret SMTP, backend uniquement |
| `EMAIL_FROM` | vide | Adresse d’expédition configurée |

Le mode sans TLS est réservé à un serveur SMTP local (`localhost`/loopback), pour les tests. Aucun courriel n’est envoyé lorsque `EMAIL_ENABLED=false` ; les jobs restent en phase `EMAIL`, le PDF demeure téléchargeable. Tester la génération et la livraison sur une adresse vérifiée contrôlée avant activation. Ne pas mettre les secrets SMTP dans React.

La livraison SMTP est au moins une fois : une coupure après acceptation SMTP peut occasionner une seconde copie avec le même `Message-ID`. Après cinq échecs d’une phase, le job conserve son diagnostic générique et doit être repris par une procédure d’exploitation (`attempts=0`, `next_attempt_at=clock_timestamp()`) après correction de la cause. Aucune marque « envoyé » n’est créée avant confirmation SMTP.

## Conservation

Les nouvelles tables portent les échéances de rétention configurées. Les accords et les modèles publiés refusent les modifications/destructions ordinaires. Une destruction de conservation n’est admise qu’avec la transaction de purge dédiée et après leur échéance propre ; les FK continuent de protéger les dépendances. Les contrats constituent des preuves signées : ne pas les modifier lors d’une suppression de compte ou d’une édition de modèle. Leur purge et celle de leurs versions MinIO doivent passer par la procédure de conservation autorisée, avec journal et reprise.

## Vérification

Tests unitaires : signature blanche refusée, PNG arbitraire refusé, signature réencodée, profil non signé bloqué, absence de cumul entre profils, confirmation parent obligatoire, URL HTTPS exigée, injection d’en-têtes SMTP refusée, PDF paginé avec signature et preuves du représentant. Les tests utilisent des noms, signatures et URL synthétiques.

Avant déploiement : fournir les vraies conditions et modèles ; appliquer V49 ; vérifier le bucket privé, la génération d’un PDF et son téléchargement autorisé ; vérifier l’adresse Keycloak ; tester SMTP si activé. Les tests locaux n’envoient aucun courriel réel.
