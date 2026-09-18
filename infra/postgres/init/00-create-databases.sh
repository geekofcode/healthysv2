#!/bin/sh
set -eu

create_role_and_database() {
  database_name="$1"
  database_user="$2"
  database_password="$3"

  psql --set=ON_ERROR_STOP=1     --username "$POSTGRES_USER"     --dbname "$POSTGRES_DB"     --set=database_name="$database_name"     --set=database_user="$database_user"     --set=database_password="$database_password" <<-'EOSQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'database_user', :'database_password')
WHERE NOT EXISTS (
    SELECT 1 FROM pg_roles WHERE rolname = :'database_user'
) \gexec

SELECT format('CREATE DATABASE %I OWNER %I', :'database_name', :'database_user')
WHERE NOT EXISTS (
    SELECT 1 FROM pg_database WHERE datname = :'database_name'
) \gexec
EOSQL
}

create_role_and_database "$HEALTHYS_DB_NAME" "$HEALTHYS_DB_USER" "$HEALTHYS_DB_PASSWORD"
create_role_and_database "$KEYCLOAK_DB_NAME" "$KEYCLOAK_DB_USER" "$KEYCLOAK_DB_PASSWORD"
