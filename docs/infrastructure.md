# Healthys infrastructure

PostgreSQL, Keycloak and MinIO are external services in every environment,
including local development. This repository does not start Docker containers
for them.

## Local development

1. Create the local environment file:

   ```bash
   cp .env.example .env
   ```

2. Replace every `replace-me` value and configure the real MinIO endpoint.
   Confirm the Keycloak realm name in `KEYCLOAK_ISSUER_URI`.
3. Start the API:

   ```bash
   ./mvnw spring-boot:run
   ```

Spring Boot imports `.env` through `spring.config.import`. Operating-system
environment variables take precedence over values in that file. The file is
ignored by Git and must never contain credentials intended for source control.

## Required variables

| Service | Variables |
| --- | --- |
| PostgreSQL | `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` |
| Keycloak | `KEYCLOAK_ISSUER_URI` |
| MinIO/S3 | `STORAGE_ENDPOINT`, `STORAGE_BUCKET`, `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY` |
| Optional tuning | `STORAGE_REGION`, `STORAGE_PATH_STYLE_ACCESS`, `DB_POOL_MAX_SIZE`, `DB_POOL_MIN_IDLE`, `DB_CONNECTION_TIMEOUT_MS` |
| Migrations | `FLYWAY_ENABLED` |

The PostgreSQL URL must use JDBC syntax:
`jdbc:postgresql://82.112.253.226:5432/healthys`, not an `http://` URL.
Add the SSL parameters required by the server, for example
`?sslmode=require`, only when TLS is configured there.

The Keycloak issuer is a realm URL, such as
`https://keycloak.wouri.tv/realms/healthys`, not only the server base URL.
The realm and clients must already exist on the remote Keycloak instance.

The MinIO bucket and access policy must already exist on the remote instance.
Use `STORAGE_PATH_STYLE_ACCESS=true` for a standard MinIO deployment.

## Staging and production

Run with `SPRING_PROFILES_ACTIVE=staging` or `prod` and inject the same
variables using the deployment platform's secret manager. Do not deploy a
`.env` file containing production secrets.

Flyway applies the SQL migrations when the API starts. On a shared remote
database, use a dedicated development database or restricted account whenever
possible. If migrations are executed separately by the deployment pipeline,
set `FLYWAY_ENABLED=false` for the application. Never edit an already applied
migration; add a new versioned migration instead.

The Actuator health endpoint remains available at `/actuator/health`.
Database connectivity is included automatically in the application health.
