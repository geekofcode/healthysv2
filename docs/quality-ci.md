# End-to-end business tests and CI

`CashCareJourneyIntegrationTests` couvre le parcours courant accueil → facturation cash → programmation → consultation → suivi gratuit, avec reports, changement de médecin, isolation entre établissements, expiration/révocation des accès, quotas et offres sans IA. `CutoverIntegrationTests` vérifie les preuves de bascule et le refus des anciennes écritures après activation. Les tests de conservation et migration restent associés aux données historiques.

Le parcours historique incluant laboratoire, officine, messagerie et téléconsultation ne constitue plus une recette du produit actif. Les tests spécifiques aux modules retirés sont retirés avec leur code opérationnel ; les tests du socle ne sont pas désactivés pour masquer une régression.

`SecurityIntegrationTests` checks all ten configured roles on real administration, professional and
organization endpoints, an expired JWT, and unauthorized upload. `DocumentApiIntegrationTests` checks
file signatures against the declared media type and extension.

Run locally with Docker available:

```sh
./mvnw test
./mvnw verify
cd frontend && npm ci && npm test && npm run build
```

On pushes to `main` and `feat/**`, pull requests, or manual `workflow_dispatch` runs, GitHub Actions
runs Maven `verify` (including Testcontainers),
React tests/build, and a Docker Compose image build. JaCoCo XML and HTML reports and Surefire
results are uploaded as the `backend-test-coverage` artifact even when a test fails.

To enable Sonar analysis in the Maven job, configure repository secret `SONAR_TOKEN` and repository
variables `SONAR_PROJECT_KEY` and `SONAR_ORGANIZATION`. Without these values the Sonar step is skipped;
tests, coverage and Docker builds still run. The Sonar step reads `target/site/jacoco/jacoco.xml`.

Docker image checks use the official Docker image namespace on ECR Public through the `DOCKER_LIBRARY` build argument, avoiding shared-runner Docker Hub rate limits. Local and production builds continue to default to `docker.io/library`. The image names and version tags are unchanged.
