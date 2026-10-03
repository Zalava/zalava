# Zalava PostgreSQL runtime configuration

Zalava supports PostgreSQL as its database. Run PostgreSQL with a database and
credentials supplied from the operator's secret store, then set these values in
the user-owned Zalava environment file:

```bash
ZALAVA_POSTGRES_JDBC_URL='jdbc:postgresql://reachable-postgresql-host:5432/zalava'
ZALAVA_POSTGRES_JDBC_USERNAME='zalava'
ZALAVA_POSTGRES_JDBC_PASSWORD='from-your-secret-manager'
```

The container launcher requires all three values and forwards them as Spring
datasource settings. Verify `/actuator/health`, an authenticated browser
session, and the JobRunr dashboard after deployment.
