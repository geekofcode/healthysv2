# Profils et parcours de consultation

## Autorisation

Keycloak identifie le compte. La base HEALTH’YS contient les profils et leurs sources. Un seul profil choisi est actif : aucun cumul des droits de plusieurs établissements ou dossiers familiaux. Le contrat signé, la licence, les droits CRUD et l’autorisation clinique restent obligatoires.

| Profil actif | Contenu |
| --- | --- |
| Patient | Activités, consultations, agenda et demandes, documents, factures de son dossier sélectionné ; identité, déclarations de santé, mesures historiques, contacts d’urgence et photo |
| Administration plateforme | Statistiques globales/par établissement, organisations, offres, souscriptions, comptes et réinitialisations, événements administratifs et authentification sans charges cliniques |
| Administration hôpital | Statistiques de son établissement, employés, rôles et permissions, tarifs/durées par spécialité, facturation et programmation selon permissions |
| Médecin / infirmier | Consultations autorisées, calendrier et file de son affectation active |
| Autres rôles hospitaliers | Menus correspondant exactement à leurs permissions effectives |

Les rôles par défaut médecin et infirmier sont réservés ; leur suppression est interdite. Les autorisations fonctionnelles ne remplacent pas la qualification et l’affectation clinique.

## Accueil et paiement

La réception recherche l’identité partagée par téléphone ou pièce. Un patient sans compte d’authentification peut être accueilli. Une nouvelle identité n’est pas fusionnée automatiquement sur une simple adresse email.

L’épisode est `DRAFT`. Le motif, la spécialité, les paramètres et les symptômes sont renseignés, le médecin ou infirmier qualifié est affecté. Le tarif et la durée configurés pour la spécialité sont conservés dans l’épisode : modifier le tarif ultérieurement ne modifie pas une facture existante.

Le paiement intégral cash autorise l’épisode `ACTIVE` et la programmation. `startedAt` reste vide. Les paiements partiels, restitutions et reçus gardent leur traçabilité existante ; aucun paiement électronique patient n’est ajouté.

La première rencontre effective démarre la visite et fixe une seule fois `startedAt` et `expiresAt`. Le rapport distingue notes, diagnostic, traitement, suivi et demandes d’examens textuelles. Chaque visite possède ses révisions et prescriptions.

## Paramètres et profil patient

Un catalogue extensible propose les paramètres usuels de santé et les constantes. Il ne prétend pas définir une liste universelle exhaustive. Une valeur conserve son type, unité, date de mesure, patient, source, auteur et visite éventuelle. Les corrections référencent une valeur antérieure ; l’historique n’est pas écrasé. Les informations déclarées par le patient sont distinguées des mesures professionnelles et ne remplacent pas les faits cliniques validés.

Un parent agit sur le dossier du mineur sélectionné avec sa propre identité, jamais sur tous les enfants simultanément. Photo privée et téléchargement soumis au profil actif.

## Agenda, file et suivi

Une demande patient, téléphonique ou à l’accueil est `PENDING`. Elle ne réserve pas un créneau et n’accorde aucun accès clinique. Son approbation hospitalière ouvre la préparation d’une visite ; l’encaissement reste requis pour une nouvelle consultation.

Le calendrier et la file sont propres au professionnel et au jour de l’établissement. L’ordre initial est celui de l’enregistrement. Appel, présence, report et réorganisation persistent. La fermeture d’une journée ou d’une plage horaire bloque les nouveaux créneaux ; les visites déjà prévues deviennent à réaffecter explicitement par l’hôpital, sans perte du paiement ou de l’épisode.

Une visite de suivi est une visite distincte liée à la première visite (`parent_visit_id`) et au même épisode. Dans le même établissement et avant expiration, elle ne génère pas une nouvelle facture couverte ; le professionnel peut changer selon disponibilités et qualification. Au-delà de la période, une nouvelle prise en charge payante est nécessaire. Les demandes d’examens n’activent pas de module d’exploitation laboratoire.

## Livraison

Les migrations V54–V58 complètent le catalogue patient, les rôles réservés/réinitialisations, le parcours, les demandes de rendez-vous et les examens du rapport. L’environnement pilote doit configurer des tarifs/durées par spécialité avant l’accueil de nouvelles consultations. Tester la migration et restaurer une sauvegarde avant bascule conformément à la procédure Phase 7.
