# Phase 2 — licences établissements

Base `/api/v1/licensing`; authentification JWT sauf webhook signé. Les réponses utilisent les noms PostgreSQL snake_case. Erreurs de permission: 403; entrées métier invalides: 400.

- GET `/plans`; POST `/plans`; PUT `/plans/{id}` (écriture PLATFORM_ADMIN).
  Entrée: name, amountMinor (entier unité monétaire minimale), currency, interval (MONTH/YEAR), doctorQuota, professionalQuota, cashierQuota, accountantQuota, aiAssistantEnabled, stripePriceId, active.
- GET `/organizations/{id}/entitlements`: snapshot souscrit, statut Stripe, period_start/end, active calculé en temps réel, usage par catégorie doctor/professional/cashier/accountant/administrator.
- GET/POST `/organizations/{id}/team`: entrée personId ou email, role. Invitation réservant un quota durant sept jours, sans envoi email automatique. Inviter une personne connue ou partager son identifiant d'invitation.
- PUT `/organizations/{id}/team/{memberId}`: role, status ACTIVE/INACTIVE. Réactivation et changement de rôle sous verrou établissement.
- POST `/organizations/{id}/team/{memberId}/accept`: seul le destinataire identifié, ou possesseur du même email vérifié Keycloak, accepte. Réservation déjà comptée; expiration interdit acceptation.
- POST `/organizations/{id}/checkout`: {planId}, retourne {url}. Stripe Checkout sur le compte HEALTH'YS, jamais un encaissement patient.
- POST `/organizations/{id}/portal`: retourne {url}; portail Stripe pour renouvellement/annulation/changement d'offre.
- GET `/organizations/{id}/invoices`: factures Stripe et liens hébergés.
- POST `/stripe/webhook`: corps exact non transformé et Stripe-Signature. Signature HMAC SHA256, tolérance cinq minutes, événements uniques. En cas d'erreur toute transaction est annulée et Stripe doit réessayer. L'état réel Stripe est relu sous verrou établissement pour éviter les mises à jour hors ordre.

Secrets: `healthys.licensing.stripe.secret-key`, `healthys.licensing.stripe.webhook-secret`, `healthys.licensing.stripe.return-url`. Sans clés, opérations Stripe fermées. API Stripe épinglée à 2024-06-20; configurer l'endpoint webhook avec cette version. Aucun appel Stripe dans les tests.

Les droits sont copiés à la souscription et conservés pour la même offre, même si le catalogue évolue. Un changement effectif de prix/offre Stripe remplace le snapshot. État active et échéance future exigés (pas de grâce ni accès trialing). Après baisse des quotas, aucun ajout n'est autorisé au-delà du plafond; les membres existants ne sont pas arbitrairement supprimés. Le personnel médical doit aussi posséder son profil et affectation professionnelle.

Administrateur local: `organization.organization.administrator_person_id` ou membre HOSPITAL_ADMIN actif; aucun rôle global HOSPITAL_ADMIN ne suffit pour administrer tous les établissements. L'administrateur initial peut souscrire sans licence préalable. Les modifications de catalogue et équipe sont tracées dans licensing.audit_entry.

GET `/my-memberships` retourne des objets camelCase organizationId, organizationName, role pour sélectionner le contexte (administrateur initial inclus). GET `/my-invitations` retourne les réservations valides de l'identité courante ou de son email Keycloak vérifié; aucun email arbitraire passé en paramètre n'est accepté.

Activation DOCTOR/NURSE exige un profil professionnel ACTIVE correspondant, et toute demande d'inscription existante doit être APPROVED. L'affectation clinique est créée si nécessaire; désactivation ou passage vers un rôle administratif termine les affectations actives dans cet établissement. Aucun onboarding n'est approuvé par l'acceptation d'invitation.

Le checkout relit le prix Stripe et vérifie montant, devise et récurrence. Une session en attente est conservée/réutilisée sous verrou avec snapshot JSON des droits au moment de l'achat. Un prix déjà souscrit ne peut changer ses paramètres financiers ni sa référence Stripe; créer une nouvelle offre.

## Configuration et validation

Configurer `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` et `STRIPE_RETURN_URL` dans l'environnement backend (`docker-compose.yml` importe déjà `.env`). Créer les prix récurrents dans le compte Stripe HEALTH’YS, puis enregistrer leurs identifiants dans les offres. Activer dans le portail Stripe les changements vers les prix des offres disponibles. Brancher le webhook sur `/api/v1/licensing/stripe/webhook` pour les événements de souscription et de facture.

Les anciennes écritures d'invitations professionnelles sont remplacées par les invitations d'équipe avec réservation de quota. Les affectations cliniques actives existantes sont reprises dans la migration V28; les administrateurs locaux doivent être désignés explicitement.

Validation locale: compilation Java 21, 37 tests backend ciblés, 98 tests frontend et build frontend. Les suites PostgreSQL/Testcontainers (migrations et concurrence des quotas) nécessitent Docker, indisponible dans cet environnement. Un paiement Stripe en mode test avec webhook réel reste à vérifier avec les clés de l'environnement cible. La phase 2 fournit le contrôle des droits IA; le service IA sera raccordé dans sa phase dédiée.
