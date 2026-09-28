# Zalava external module template

Copy this directory into a new repository, rename the module coordinates and
identifiers, then replace the sample provider with the module's explicit tools,
resources, prompts, metadata, and tests.

## Local development

Use the released SPI from GitHub Packages. Configure `gpr.user` and `gpr.key`
in user Gradle properties; do not commit credentials. For an unreleased core
change, override `moduleApiVersion` and `moduleApiRepositoryUrl` with a local
Maven repository.

When Zalava creates a development workspace, it is authoritative for the SPI
coordinate: read `.sea-request/sdk/module-api.coordinates` and pass that
version as `-PmoduleApiVersion=...`. Do not guess or copy a version from a
different module; Zalava owns the compatibility contract and must tell each module
which released module API it targets.

Run `gradle test` before every PR. Stable releases require an immutable
`vX.Y.Z` tag. Patch versions preserve the public contract, minor versions add
compatible API, and major versions may break it.

Update `module-metadata.yaml` for every release with the artifact version,
source license, supported Zalava runtime range, operations, and permissions. The
build validates this document and embeds it at the root of the installable JAR
so Zalava can install the artifact from a local file without network access.
Generate and publish the JAR SHA-256 alongside the release.

## End-to-end acceptance

Every new module or capability must include tests using its actual distributable
artifact inside Zalava. Keep these scenarios in the module repository and run them
on module PRs against explicitly pinned compatible Zalava and browser-kit versions.
Verify installation/configuration and restart where required, then use the
capability through its real entry point. For chat tools, script model tool calls
but keep actual module execution, permissions and persistence real. Use local
fixtures for external services; no real AI or production credentials are required.

For a module-owned app, open it through Zalava's Apps navigation and exercise its
real forms, scripts and assets. Assert visible and persisted changes, reload/
restart behavior, applicable session/CSRF and permission failures, and state
parity with tools. For example, add a shopping-list item, mark it bought, reload,
and verify the tool observes the same bought state. App actions need no model.

Include successful use and relevant failure/denial cases for each capability,
plus regression coverage for reproduced bugs. Record exact tested artifacts and
retain failure traces, screenshots and logs without committing generated output.
Bean registration, direct tool calls or rendered HTML alone are not E2E proof.

The shared kit is planned in Zalava's `E2E-MODULE-01`; do not assume it is already
published or invent a dependency coordinate. Until it is available, record the
scenarios, current evidence and missing automation explicitly in the module
plan/PR. Once available, adopt its documented local/CI commands as a required
lane: missing prerequisites must fail rather than silently skip tests. Carry
this requirement into the new repository's contributor/agent instructions.

## Typed services

Services are deterministic module/runtime contracts, not agent tools. Declare
provided and required services under `services` in `module-metadata.yaml`, and
return matching `SeaServiceFactory` and `SeaServiceRequirement` values from
`SeaModule`. A content-extractor module provides
`ContentExtractor.CONTRACT`; its extractor receives only one bounded source
stream and must return `ContentExtractionResult.forRequest(...)` or
`ContentExtractionFailure.forRequest(...)`. Do not expose paths, database
handles, Spring objects, or storage mutation through a service.

## Installability gate

A migrated module is complete only when it can be installed through Zalava. Before
calling a release installable, prove: a matching `LICENSE` and source-license
metadata; a published `vX.Y.Z` JAR whose `SeaModule` descriptor reports
`X.Y.Z`; a module-owned immutable `releases/index.yaml` entry; a locator entry
in `cordin/zalava-module-index`; and a Zalava UI proof of selection, approval,
checksum verification, enablement, host-owned restart, and provider discovery.

The locator catalog contains only a repository URL and `releases/index.yaml`
path. Never duplicate versions, digests, or credentials there.
Private repository credentials are Zalava secret-bound; never place them in a
manifest, locator catalog, browser request, or installation record.

## Repository workflow

Keep one plan, branch, draft PR, and release version per module change. CI
verifies before publication and publishes only with the GitHub Actions token.
