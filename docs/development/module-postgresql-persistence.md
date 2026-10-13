# Module PostgreSQL persistence and migrations (design proposal)

Status: proposed; implementation and shopping-list adaptation require separate reviewed PRs.

## Context and constraints
Zalava is Java 25, Spring Boot, PostgreSQL, Flyway and independently released JVM modules. Respect hexagonal boundaries: domain/use cases use ports; JDBC adapters implement them. Public module API contracts must not depend on host internals. Core owns installation and persistence acceptance.

## Decision
Offer an **optional** module database capability through the public Module API. Modules opt in through existing module metadata/capability conventions (inspect current contracts before finalizing the declaration). Reuse Zalava's configured PostgreSQL datasource and transaction manager, with one stable PostgreSQL schema and one Flyway history table per module. Do not introduce Kotlin or another persistence framework.

The existing `module-api/build.gradle` enforces **JDK-only production dependencies** with `jdkOnlyCheck`. Therefore do **not** expose Spring `JdbcClient`, `TransactionTemplate`, or other Spring types in the public API. Keep Spring JDBC and Flyway in the host. The existing `ProviderFactoryContext` already supports module-scoped services; extend this mechanism rather than inventing a new injection system.

Proposed Java contract (illustrative; finalize checked-exception and connection-lifetime semantics during implementation):
```java
public interface ModuleDatabase {
    <T> T withConnection(SqlFunction<Connection, T> work);
    <T> T inTransaction(SqlFunction<Connection, T> work);
}

@FunctionalInterface
public interface SqlFunction<T, R> {
    R apply(T value) throws SQLException;
}
```
The host provides schema-scoped, transaction-aware connections and controls lifecycle, rollback and cleanup. Callbacks must not retain or close host-owned connections. The module uses JDBC without its own datasource, database credentials, or connection pool. Test transaction nesting and search-path leakage. Keep the API compatible with independently released module JARs.

## Module packaging
Suggested resources: `db/migration/V1__create_tables.sql`, `V2__add_index.sql` in the module JAR, discovered with its own classloader. Define a stable, collision-resistant schema derived from immutable module ID; never use display name or unchecked SQL identifier input. Configure Flyway per module with explicit migration locations, default schema and schema history; never mix module migrations into the core Flyway history.

## Lifecycle and safeguards
1. Validate module compatibility, permissions and migration metadata before activation.
2. Acquire a per-module migration lock (safe across multiple host processes).
3. Provision schema if permitted, or report actionable external-DB setup instructions.
4. Run Flyway validate/migrate against the module-specific history, using the module classloader.
5. Expose scoped DB capability and activate only after migration success.
6. Record failures and keep module inactive; retry must be explicit and idempotent.
7. On disable/uninstall retain data by default. Never automatically invoke Flyway clean; no implicit downgrade/rollback of database schema.

Ensure search_path/schema selection is transaction-safe and cannot leak between pooled connections; test nested transactions, connection reuse and concurrency. A shared database user does **not** provide strong isolation: enforce module boundaries in API and document privilege limitations. Cross-module data exchange uses services, not direct table reads.

## Shopping-list pilot (Zalava/zalava-module-shopping-list)
This public module is independently released and its README states that the host verifies persistence. Use it as the first end-to-end consumer, after auditing its current storage adapter and state lifecycle.

- Identify its existing shopping lists/items storage, serialization, ownership and restart behavior; document current behavior before migration.
- Introduce a repository port in its application/domain boundary if absent; implement it in a Java JDBC adapter using ModuleDatabase.
- Package Flyway SQL for lists/items, keys, constraints, indexes and optimistic locking `version` columns where updates require concurrency protection. Preserve module IDs and user ownership semantics.
- **Explicit scope decision:** migration of existing Shopping List installations and their SQLite data is **not required**. New PostgreSQL-backed installations may start with empty tables. Do not implement SQLite-to-PostgreSQL import, backward compatibility, or a dual-storage migration mechanism solely for this change. Document the storage change clearly so existing installations are not mistakenly assumed to retain their SQLite data.
- Test CRUD, existing authorization semantics, concurrent updates, failure/retry, module disable/re-enable, restart persistence, PostgreSQL schema upgrades and uninstall preservation. No legacy SQLite migration tests are required.
- Verify the **built released-style JAR** using the module contract kit and in a real host against PostgreSQL; kit-only tests are insufficient.
- Coordinate Module API release/compatibility and shopping-list module dependency upgrades; never require an unreleased API version in a published module.

## Implementation sequence
1. Audit actual module loader and activation path, existing Flyway setup, `ProviderFactoryContext` service wiring, and shopping-list persistence (currently `SqliteShoppingListRepository`, constructed by `ShoppingListProviderFactory`).
2. Finalize the capability contract, classloader ownership and database transaction strategy.
3. Implement host migrations/lifecycle and module API with unit/integration tests (PostgreSQL).
4. Adapt shopping-list repository and SQL migrations; add real-host acceptance tests.
5. Document external DB privileges/schema provisioning, operations and recovery.
6. Open separate dependent PRs, run each repository's mandated verification and keep them unmerged until review.

## Acceptance criteria
- A Java module obtains persistence without credentials or datasource configuration.
- Two modules migrate independently with no schema/history collisions.
- Migrations execute once per version and recover safely after interrupted upgrades.
- No cross-module pooled-connection schema leakage.
- New PostgreSQL shopping-list data survives PostgreSQL schema upgrades, restart and disable/re-enable; migrating pre-existing SQLite installations is explicitly out of scope.
- Migration failure blocks only affected module activation with actionable diagnostics.
- External PostgreSQL deployments have a documented least-privilege provisioning path.
- All CI/coverage, contract-kit and real-host tests pass; no autonomous merge.

## Verified source-level feasibility (2026-10-13)

- Host `app/build.gradle` already includes Spring Data JDBC, Spring Boot Flyway starter, Flyway PostgreSQL support, PostgreSQL JDBC driver, and PostgreSQL Testcontainers. No additional persistence framework is needed.
- `module-api/build.gradle` requires JDK-only production dependencies. A Spring-type API would fail its enforced architecture boundary.
- `ProviderFactoryContext` already scopes services by module and factory; leverage this extension point for the database capability.
- Host `application.yaml` currently configures core Flyway locations. Module migrations must run separately with their own schema and history, not be appended to core locations.
- Shopping List already defines `ShoppingListRepository`; `ShoppingListProviderFactory` directly constructs `SqliteShoppingListRepository` using `ShoppingListStorage.path()`. Replace that construction with the new JDBC adapter while preserving the existing repository port and operation behavior.
- Shopping List `build.gradle` bundles `sqlite-jdbc` at runtime, and `module-metadata.yaml` declares `sqlitePath` and `local-storage`. Update metadata and dependencies for the new host database capability; avoid bundling host API classes.
- SQLite SQL uses integer active flags, text timestamps, `AUTOINCREMENT`, `COLLATE NOCASE`, and a partial unique index. Port to PostgreSQL boolean, timestamptz, identity columns, explicit case-insensitive ordering and a PostgreSQL partial index.
- The complete installation/activation path and dynamic module classloader resource discovery still require implementation-time audit and PostgreSQL integration tests. This is source-level feasibility, not a passing end-to-end verification.
