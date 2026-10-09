# End-to-end business tests and CI

`ClinicalJourneyIntegrationTests` runs through the HTTP API with PostgreSQL Testcontainers and Flyway:
organization, doctor and laboratory technician, patient registration, care relationships and consent,
appointment, consultation and diagnosis, prescription, laboratory order, specimen, final result,
and patient access. It also checks access refusal for a different patient and organization, plus audit entries.
The same test transaction rolls back its business data; independently committed access decisions remain in
`audit.data_access_log`.

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
