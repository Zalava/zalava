# Zalava contributor guidance

## Bounded contexts and ports

Organize product code by bounded context / feature slice. A context owns its
domain language, use cases, and persistence-facing contracts; it does not reach
into another context's internals. Share a stable contract only when the
relationship is deliberate and versioned.

Keep each context hexagonal. Domain rules must not import Spring, HTTP,
persistence, messaging, or vendor-SDK types. Inbound adapters translate a
transport request into a use-case input. Use cases coordinate domain behavior
through explicit inbound and outbound ports. Outbound adapters implement those
ports and contain framework and infrastructure mapping. Dependencies point
inward, and Spring wiring remains at a composition boundary.

Do not introduce a Gradle subproject merely to represent a context. Split build
modules only when independent compilation, release, ownership, or dependency
isolation requires it. Prefer an incremental vertical-slice migration to a
broad architectural rewrite.

Zalava is a Java 25 Gradle multi-project application with a Spring Boot host,
PostgreSQL persistence, and independently released JVM modules.

Run the Gradle wrapper from the repository root. Use focused tests first and the
documented verification task before review. Keep tests deterministic and test
observable behavior; HTTP behavior uses full-context MockMvc tests and product
journeys use the repository browser acceptance lane when available.

Keep policy and validation at system boundaries. External modules compile against
the released Module API, must not bundle it, and preserve declared identifiers
unless a reviewed compatibility change says otherwise. Do not commit credentials,
user data, local configuration, generated browser output, or build products.

## Verification and delivery

Use imports and simple class names in Java code instead of fully qualified class
references in declarations or expressions. Keep a qualified reference only when
an actual name collision makes importing both types impossible. Apply this rule
to production code, tests, and templates; formatting alone does not enforce it.

The host's source ownership groups are documented in `docs/development/architecture.md`.
Do not reintroduce peer packages for small technical helpers or build projects
solely to represent contexts. Keep public SDK contracts distinct from host adapters.

Before review, run `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :module-api:test
:module-api-test:check :app:check`. `check` includes formatting, architecture,
browser acceptance and JaCoCo coverage verification: host minimum 90% line / 74%
branch, API/kit minimum 90% line / 70% branch. Do not lower thresholds or exclude
production code to make a change pass. HTTP regressions use full-context MockMvc;
browser and persisted-outcome acceptance must cover observable changes.

Apply these delivery criteria to external modules as far as their entry points
allow: 90% line / 74% branch coverage and the same Spotless/google-java-format
checks enforced by `check`; deterministic tests plus the built artifact through
the released contract kit; configuration, permissions, validation/failure,
cleanup and observable outcomes. Kit acceptance is not real-host acceptance.
Core owns install/restart/security/persistence/browser journeys against released
module artifacts. Missing prerequisites are reported, never counted as passing.

Use an explicit step branch, scoped evidence, tests, staged-diff review and a
ready-for-review PR. Use `gh stack` for dependent PRs. The repository-explicit
`scripts/zalava-workflow` supports verification and publication across public
repositories, with a separately persisted plan and explicit changed-file allowlist.
Never merge autonomously; publish only immutable versions from verified merged
default-branch commits and successful publication workflows.

## Private plan ownership (user directive, 2026-10-03)

Keep every dated roadmap/execution plan and private acceptance record only in
`cordin/zalava-dev` (`/home/cordin/Projects/sea-projects/sea`). Never create or copy
`docs/plans/` in this public product repository. Public contributor, architecture,
API, and product documentation stays here. Publication must take an explicit
private-plan path and must not stage that plan in the product PR.
