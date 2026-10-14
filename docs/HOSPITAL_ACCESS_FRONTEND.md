# Administration plateforme et accès des établissements

L’administration plateforme affiche uniquement les statistiques agrégées, les identités minimales de compte, les établissements et leurs licences. Elle ne charge plus les listes de patients, les équipes, les journaux contenant des dossiers médicaux ni les opérations de caisse. Après identification, chaque nouvelle session doit choisir explicitement un seul profil actif parmi les profils vérifiés par le backend, même lorsqu’un seul profil existe. Le profil plateforme reste exclu des opérations hospitalières ; la même personne peut utiliser séparément un profil médical réellement rattaché à un établissement, sans hériter des privilèges plateforme.

La création d’un établissement demande soit les nom/prénom/email de son administrateur, soit un compte existant. Les mots de passe ne sont jamais saisis par l’administrateur ; le backend organise la provision et l’activation du compte.

Le menu **Gestion des employés** permet de créer un compte, rechercher un compte existant par email exact, choisir un profil métier, désactiver/réactiver un membre et affecter plusieurs rôles d’accès. Les médecins/infirmiers disposant d’un profil professionnel peuvent ouvrir sa configuration et ses disponibilités. La validation des justificatifs est dans **Validation des profils**, limitée à l’établissement par le backend.

**Rôles et accès** utilise le catalogue fourni par le backend : une ligne par ressource fonctionnelle, quatre colonnes CREATE/READ/UPDATE/DELETE. Les actions non disponibles dans le catalogue restent désactivées. La version du rôle accompagne les modifications pour détecter les éditions concurrentes. Les rôles affectés remplacent les droits implicites du profil ; leurs permissions se cumulent. La création d’un employé et l’affectation de ses rôles se font dans une seule transaction backend. Un nouvel employé sans rôle explicite reçoit une politique vide et aucun accès opérationnel.

Les menus, routes et actions des interfaces reflètent les permissions effectives de l’établissement sélectionné. Le chargement et les erreurs des permissions échouent en accès fermé. Les droits sont réactualisés périodiquement et après modification ; le backend reste la décision finale à chaque requête. Les permissions ne remplacent jamais les qualifications médicales, l’affectation du médecin, la licence d’établissement ou l’autorisation temporaire du dossier patient.

La matrice concerne les ressources métier et leurs interfaces, pas l’exposition des tables PostgreSQL : les tables techniques, secrets, historiques immuables et journaux ne deviennent pas des écrans CRUD.

## Plusieurs profils pour une même personne

Le sélecteur **Profil actif** utilise `GET /api/v1/me/access-profiles`. Deux profils médecin dans des établissements différents sont affichés séparément, et deux profils métier au sein du même établissement restent distincts. Les rôles de plusieurs profils ne sont jamais additionnés ; le frontend expose uniquement le `type` du profil actif vérifié.

Les requêtes portent `X-Profile-ID` ainsi que le `X-Organization-ID` correspondant, lorsque le profil appartient à un établissement. Le choix personnel ou plateforme retire l’organisation. Le backend doit vérifier la propriété, l’état et la correspondance du profil à chaque requête ; le stockage local n’accorde aucune permission. La découverte des profils n’envoie pas un ancien profil pouvant avoir été révoqué.

Le changement annule les requêtes, efface les caches de données et de mutations, remonte les écrans et revient à l’accueil. Une réponse tardive portant sur le profil précédent est refusée avant de pouvoir remplir le cache. Les permissions sont rechargées sous une clé comprenant l’identité du profil, y compris lors d’un changement dans le même établissement.

## Choix obligatoire, profils familiaux et contrats

Le parcours commence sur `/profiles/select`. Il n’existe plus de profil générique « compte » sélectionnable et aucun choix automatique n’est effectué. Une sélection explicitement confirmée peut être reprise lors d’un rafraîchissement de la même session ; login, logout et expiration de session retirent cette confirmation. La création autonome est limitée aux profils patient pour soi-même ou pour un mineur. Les profils professionnels et hospitaliers proviennent d’une attribution vérifiée.

`POST /me/patient-profiles` distingue SELF et MINOR. Le formulaire mineur demande la relation parent/tuteur et la date de naissance du responsable adulte (celle déjà enregistrée est affichée sans modification). L’idempotency key reste stable pour une tentative inchangée. Les profils familiaux portent `sourceType=FAMILY_PATIENT`, sont séparément sélectionnables et exigent l’attestation du responsable légal pour signer.

Le contrat de chaque profil doit être signé avant le montage des menus et écrans métier. Une catégorie sans modèle publié bloque l’accès et indique qu’une configuration plateforme est nécessaire. L’administrateur plateforme peut ouvrir uniquement la configuration des modèles avant sa propre signature pour éviter un blocage de configuration. Les modèles comportent l’URL HTTPS réelle des conditions, leur version, le texte du contrat et la zone réservée `{{signature}}`. Les versions publiées restent immuables et les signatures précédemment acceptées restent valides.

La signature exige une acceptation des conditions et un dessin manuscrit sur canvas (souris ou tactile). Le texte contextualisé est produit par le serveur, puis le PDF signé est généré et envoyé par email de manière asynchrone. L’écran affiche l’état PDF/email et permet le téléchargement authentifié lorsque prêt. Un traitement email désactivé ou défaillant n’est jamais présenté comme envoyé ; la signature autorise l’espace sans attendre ce traitement. Le polling est borné à cinq minutes. Les appels de création personnelle, de découverte et de signature de profils n’utilisent pas les anciens headers d’un profil pouvant être invalide.
