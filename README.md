# HEALTH’YS — socle consultation multi-établissement

HEALTH’YS organise l’accueil d’un patient, le paiement cash, la programmation et la consultation, puis les retours couverts dans le même établissement. Une identité patient unique permet un dossier partagé, consultable uniquement selon les autorisations temporaires effectives.

## Périmètre actif

- Administration des établissements, licences Stripe, offres, quotas et équipes.
- Fiche patient unique, accueil infirmier, constantes et épisodes de prise en charge.
- Tarifs, factures de soins, encaissements cash, reçus, restitutions et clôtures de caisse.
- Programmation, report et réaffectation des visites, calendrier et file médecin.
- Consultation, rapports versionnés et validés, prescriptions et documents privés MinIO.
- Suivi couvert dans le même établissement, accès temporaires et audit.
- Assistant Python/OpenAI facultatif selon l’offre : résumé, rédaction assistée et dictée.
- Conservation configurable (30 ans par défaut), demandes de suppression et migration historique contrôlée.

La téléconsultation, le carnet mère-enfant, la messagerie, l’exploitation laboratoire, la dispensation et les stocks pharmacie sont hors du périmètre actuel. Les données et migrations historiques sont conservées pour préserver l’historique et permettre la bascule ; elles ne doivent pas réactiver ces parcours.

## Stack

Java 21, Spring Boot/Modulith, PostgreSQL/Flyway, Keycloak, MinIO ; frontend React/Vite/TypeScript ; service IA Python/OpenAI privé. Les paiements des patients sont exclusivement cash. Stripe concerne les abonnements établissements.

## Guides

- [Socle et découpage](docs/consultation-core-refactor.md)
- [Administration et licences](docs/PHASE2_LICENSING_API.md)
- [Patient et prise en charge](docs/PHASE3_PATIENT_CARE.md)
- [Facturation cash](docs/PHASE4_CASH_BILLING.md)
- [Programmation et file](docs/PHASE4_PROGRAMMING_QUEUE.md)
- [Consultation et suivi](docs/PHASE5_CONSULTATION_FOLLOW_UP.md)
- [Contrôle de caisse](docs/PHASE5_CASH_REGISTER.md)
- [Assistant IA](docs/PHASE6_AI_ASSISTANT.md)
- [Conservation, migration et livraison](docs/PHASE7_RELEASE.md)
- [Nettoyage du périmètre](docs/SCOPE_CLEANUP.md)
- [Thème Keycloak](docs/keycloak-theme.md) et [inscription professionnelle](docs/professional-registration.md)

## Bascule pilote

Le mode livré reste `LEGACY` jusqu’à validation de la migration et du parcours cash sans IA. Les commandes de l’ancien parcours consultation deviennent indisponibles après activation contrôlée ; l’historique reste consultable. Exécuter les intégrations PostgreSQL natif et une restauration PostgreSQL/MinIO isolée avant cette activation, selon le guide de Phase 7.

La [documentation mobile précédente](docs/archive/PREVIOUS_MOBILE_FEATURES.md) est archivée et ne décrit pas le périmètre actif de cette version.
