# Contrats signés par profil

Chaque profil a son propre contrat : patient et enfant mineur (`PATIENT`), administration locale (`HOSPITAL`), personnel (`STAFF`), administration HEALTH’YS (`ADMIN`). Le choix du profil est obligatoire et ne cumule pas les droits d’un autre profil. La signature d’un profil n’autorise aucun autre profil. Un changement de type fonctionnel d’une appartenance, même si son identifiant reste inchangé, exige le contrat de ce nouveau type ; l’ancien accord reste consultable comme preuve historique.

L’administrateur plateforme crée un modèle en texte brut ou en HTML restreint ou avec l’éditeur HTML contrôlé dans `/api/v1/admin/contract-templates`, fournit l’URL HTTPS réelle des conditions, leur version et le contenu contractuel, puis publie le modèle. Aucun texte juridique fictif ni URL de conditions n’est publié automatiquement. Sans modèle publié, le profil reste bloqué. Les seuls appels de configuration des modèles sont accessibles à l’administrateur sélectionné avant sa propre signature afin de permettre l’initialisation.

Les anciens accords conservent leur corps `TEXT`, leur signature et leur empreinte existante après V52. Le format est inclus dans les nouveaux instantanés signés et protégé contre les modifications.

Les variables de modèle admises sont `{{signerName}}`, `{{profileLabel}}`, `{{organizationName}}`, `{{patientName}}`, `{{signedAt}}`, `{{signature}}`. La prévisualisation remplace les informations par les valeurs du serveur ; la date exacte est fixée lors de la signature et la signature manuscrite dispose d’une page réservée. Le contenu est identifié par `bodyFormat: TEXT | HTML`. Le format HTML autorise titres, paragraphes, gras, italique, soulignement, listes, citations, tableaux et liens HTTPS. Les scripts, styles/CSS arbitraires, médias et ressources distantes sont exclus. Les valeurs des variables sont échappées avant insertion dans le HTML. Une publication est immuable : une modification nécessite une nouvelle version. **Les signatures existantes restent valables après publication d’une nouvelle version** ; les nouveaux accords utilisent la version publiée courante. Il n’y a pas de demande rétroactive silencieuse d’acceptation.

## Acceptation et génération

`GET /api/v1/me/profile-contracts/{profileId}/template` fournit le modèle courant et `renderedBodyText`. `POST /api/v1/me/profile-contracts/{profileId}/sign` reçoit uniquement l’identifiant de modèle, `acceptTerms`, une signature personnelle dessinée dans le canvas ou importée en PNG/JPEG (convertie en PNG) et `guardianAcknowledgement`. Le serveur vérifie la propriété du profil, le modèle publié, l’acceptation et une signature non blanche. Le PNG est limité, ses dimensions sont contrôlées avant décodage, puis il est réencodé sans métadonnées.

Le signataire est le compte authentifié, avec son nom en base et **l’adresse de son token Keycloak portant `email_verified=true`**. Aucun nom de signataire, destinataire, chemin de fichier ou acteur n’est accepté depuis le client. Pour le profil d’un mineur, le parent signe et doit confirmer sa qualité de représentant ; la relation parent/enfant et l’âge sont vérifiés par le résolveur de profils. L’identité de l’enfant est conservée dans le contenu signé sans remplacer le signataire.

Le contenu rendu et son format, les conditions, leur version, le profil, l’horodatage, la signature et les empreintes SHA-256 sont conservés dans un accord immuable. Le worker durable génère le PDF et conserve PDF/signature dans le bucket MinIO privé sous un chemin généré par le serveur. `GET /.../{profileId}` fournit l’état ; `GET /.../{profileId}/pdf` télécharge la copie après contrôle de propriété, avec `Cache-Control: no-store`.

L’accord accepté autorise le profil dès la signature enregistrée ; un incident de génération ou de courriel ne bloque pas de nouveau le profil. Le PDF et le courriel sont repris séparément, avec réservation de job, bail, reprises bornées et erreurs génériques sans secrets. Les jobs IA vérifient également l’accord signé du profil d’origine avant exécution.

## Configuration de livraison

Brevo est préconfiguré dans `.env.example` : `smtp-relay.brevo.com`, port 465 avec TLS implicite. Renseigner le login et la clé SMTP Brevo (distincte de la clé API), une adresse d’expédition vérifiée, puis activer `EMAIL_ENABLED`. Les secrets restent côté backend.

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

Tests unitaires : signature blanche refusée, PNG arbitraire refusé, signature réencodée, profil non signé bloqué, absence de cumul entre profils, confirmation parent obligatoire, URL HTTPS exigée, injection d’en-têtes SMTP refusée, PDF paginé avec signature et preuves du représentant. Tests HTML complémentaires : nettoyage de mise en forme, retrait des événements/styles, refus des contenus actifs, liens non HTTPS neutralisés, noms serveur échappés, marqueur de signature conservé, absence d’évaluation imbriquée des variables, corps vide refusé et sauvegarde effective du format HTML nettoyé. Les tests utilisent des noms, signatures et URL synthétiques.

Avant déploiement : fournir les vraies conditions et modèles ; appliquer les migrations de contrat, dont V49 et V52 ; vérifier le bucket privé, la génération d’un PDF et son téléchargement autorisé ; vérifier l’adresse Keycloak ; tester SMTP si activé. Les tests locaux n’envoient aucun courriel réel.

## Éditeur riche et compatibilité

L’administration propose un éditeur visuel et un mode source HTML avec aperçu contrôlé. Les modèles nouvellement créés utilisent le HTML ; les anciens modèles et accords restent `TEXT` par défaut après la migration V52. Passer un ancien modèle publié au HTML demande une nouvelle version, sans réécriture de contrats signés.

L’API conserve `bodyText` pour le contenu et ajoute `bodyFormat` (`TEXT` ou `HTML`, omis = `TEXT` pour les anciens clients). Le serveur normalise le fragment HTML sur une liste de balises admises, supprime les attributs non autorisés et refuse les éléments actifs. Il n’importe aucune image ou feuille de style extérieure. L’aperçu web applique également un contrôle avant affichage ; l’autorité reste la validation serveur.

Le PDF reproduit les titres, emphases, listes et tableaux avec pagination, puis les preuves d’acceptation et la page de signature dédiée. Il ne s’agit pas d’un navigateur HTML/CSS complet : seules les structures annoncées par l’éditeur sont prises en charge. La signature réelle reste fournie par le signataire (dessin ou image), et les jobs MinIO/courriel continuent à fonctionner sur le contenu immuable enregistré.

## En-tête et pied de page HTML

Chaque modèle peut définir `headerHtml` et `footerHtml`, en plus du corps. Ces zones utilisent le même HTML contrôlé et le même échappement des variables. L’administration peut éditer et prévisualiser les trois zones séparément. Les champs sont facultatifs ; les anciens modèles et contrats ont des zones vides après la migration V53.

Les zones configurées sont répétées sur toutes les pages du PDF, y compris les pages de preuve et de signature. `{{pageNumber}}` et `{{pageCount}}` sont réservés à ces zones et remplacés lors de la génération finale. Les autres variables de profil/signataire sont rendues au moment de l’acceptation. Leurs instantanés sont conservés avec le contrat signé et protégés contre les modifications.

Des bandes fixes et des marges sont réservées pour éviter le chevauchement avec le corps ou la signature. Une zone trop longue ou trop haute doit être raccourcie ; elle n’est pas coupée silencieusement. Le HTML des zones n’autorise ni scripts, médias ni CSS arbitraire. Sans zone personnalisée, le comportement historique est conservé.
