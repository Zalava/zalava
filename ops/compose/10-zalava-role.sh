#!/bin/sh
set -eu
# Invoked only during PostgreSQL initialization. Never enable shell tracing.
export ZALAVA_DB_APP_PASSWORD="$(cat /run/secrets/db-app-password)"
psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set ON_ERROR_STOP=1 <<'SQL'
\getenv app_password ZALAVA_DB_APP_PASSWORD
CREATE ROLE zalava LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD :'app_password';
ALTER DATABASE zalava OWNER TO zalava;
ALTER SCHEMA public OWNER TO zalava;
SQL
unset ZALAVA_DB_APP_PASSWORD
