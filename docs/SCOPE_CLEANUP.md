# Nettoyage du périmètre après Phase 7

Le socle retenu est la consultation multi-établissement, avec licences, accueil, caisse cash, programmation, dossier patient, rapports, prescriptions, suivi et IA optionnelle.

La téléconsultation, le carnet mère-enfant, la messagerie, l’exploitation des laboratoires, la dispensation et les stocks pharmacie ne font pas partie de cette version. Les retirer exige de supprimer leurs écrans et routes frontend, leurs points d’entrée backend, leurs jobs spécifiques et leur configuration, pas seulement leurs liens de menu.

Les anciennes tables et migrations Flyway ne sont pas supprimées : elles sont nécessaires au rapprochement historique et au retour arrière contrôlé. Les dépendances de stockage et de rétention peuvent encore les référencer pour protéger les dossiers partagés. Cette conservation ne constitue pas une réactivation de ces modules.

Le parcours ancien consultation/rendez-vous reste soumis au verrou de bascule de Phase 7. Il est distinct des fonctionnalités hors périmètre retirées immédiatement. Les prescriptions de la consultation courante restent disponibles ; les flux officine et les stocks sont retirés.

La vérification porte sur l’absence des anciennes routes dans le routeur et la navigation, l’indisponibilité des API correspondantes et du WebSocket de chat, la compilation et les tests du socle actif. Les anciennes migrations et les jeux de données historiques continuent de fonctionner.

Les notifications restent disponibles via HTTP/push ; le frontend utilise un rafraîchissement REST et filtre les anciens liens vers les modules retirés. Le backend ne requiert plus LiveKit ni broker WebSocket. Les nouvelles inscriptions et créations de professionnels n'acceptent que médecin/infirmier ; les types d'établissement laboratoire/pharmacie ne peuvent plus être créés. Les identités historiques correspondantes restent conservées pour les rapprochements.

Validation locale : compilation neuve, 307 tests backend et 130 tests frontend réussis, typecheck et build frontend réussis. Les tests de périmètre inspectent les composants et les routes de l'application compilée ; les tests des écrans supprimés ont été retirés avec leur code, ce qui explique le changement de nombre de tests. Les intégrations natives et la restauration pilote de Phase 7 restent des prérequis de bascule.
