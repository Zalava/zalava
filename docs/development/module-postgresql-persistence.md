# Module PostgreSQL persistence and migrations (design proposal)

Status: proposed; implementation and shopping-list adaptation require separate reviewed PRs.

## Context and constraints
Zalava is Java 25, Spring Boot, PostgreSQL, Flyway and independently released JVM modules. Respect hexagonal boundaries: domain/use cases use ports; JDBC adapters implement them. Public module API contracts must not depend on host internals. Core owns installation and persistence acceptance.

## Decision
Offer an **optional** module database capability through the public Module API. Modules opt in through existing module metadata/capability conventions (inspect current contracts before finalizing the declaration). Reuse Zalava's configured PostgreSQL datasource and transaction manager, with one stable PostgreSQL schema and one Flyway history table per module. Do not introduce Kotlin or another persistence framework.

Suggested public Java API (illustrative, adapt to existing API conventions):
```java
public interface ModuleDatabase {
    JdbcClient jdbc();
    TransactionTemplate transactions();
    String schema();
}
```
Evaluate whether exposing Spring types in the public API is compatible with existing dependency/classloader rules. If not, expose a minimal host-owned service and keep Spring types in an optional integration adapter. Modules must not bundle host-provided Spring/API classes.

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
- If existing data is stored elsewhere, provide an explicit **idempotent data migration** with backup and validation; do not silently discard data.
- Test CRUD, ownership/authorization, concurrent updates, failure/retry, module disable/re-enable, restart persistence, upgrade and uninstall preservation.
- Verify the **built released-style JAR** using the module contract kit and in a real host against PostgreSQL; kit-only tests are insufficient.
- Coordinate Module API release/compatibility and shopping-list module dependency upgrades; never require an unreleased API version in a published module.

## Implementation sequence
1. Audit actual module API, module loader, existing Flyway setup and shopping-list persistence.
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
- Existing shopping-list data survives upgrade, restart and disable/re-enable.
- Migration failure blocks only affected module activation with actionable diagnostics.
- External PostgreSQL deployments have a documented least-privilege provisioning path.
- All CI/coverage, contract-kit and real-host tests pass; no autonomous merge.
