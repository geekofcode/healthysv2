# Healthys infrastructure

## Local development

1. Copy the local environment template:

   ```bash
   cp .env.example .env
   ```

2. Change the local passwords in `.env`.
3. Start PostgreSQL, Keycloak and MinIO:

   ```bash
   docker compose up -d
   docker compose ps
   ```

4. Start the API:

   ```bash
   ./mvnw spring-boot:run
   ```

The default local endpoints are:

- PostgreSQL: `localhost:5432`, database `healthys`
- Keycloak: `http://localhost:8081`, realm `healthys`
- MinIO API: `http://localhost:9000`
- MinIO console: `http://localhost:9001`

The PostgreSQL initialization script creates isolated `healthys` and
`keycloak` databases and roles. Flyway applies `V1` through `V7` to the
Healthys database when the API starts. Keycloak imports the local realm on its
first start. MinIO creates the application bucket automatically.

## Staging and production

Do not start `compose.yml` on staging or production. Run the application with
`SPRING_PROFILES_ACTIVE=staging` or `prod` and inject these secrets through
the deployment platform:

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `KEYCLOAK_ISSUER_URI`
- `STORAGE_ENDPOINT`, `STORAGE_REGION`, `STORAGE_BUCKET`
- `STORAGE_ACCESS_KEY`, `STORAGE_SECRET_KEY`
- `STORAGE_PATH_STYLE_ACCESS`

The remote PostgreSQL JDBC URL has the form
`jdbc:postgresql://82.112.253.226:5432/healthys`. The `http://` prefix must
not be used for PostgreSQL. The expected Keycloak issuer has the form
`https://keycloak.wouri.tv/realms/<realm>`; confirm the deployed realm name
before configuring it.

Flyway remains enabled in staging and production, so the same versioned
migrations are applied exactly once. Never edit a migration that has already
run remotely; add a new migration instead.

## Reset local infrastructure

This deletes local PostgreSQL and MinIO data:

```bash
docker compose down --volumes
docker compose up -d
```
