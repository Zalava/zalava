# Released module acceptance

`./gradlew :app:releasedModuleAcceptanceTest`
runs the packaged host as a separate `java -jar` process against disposable
PostgreSQL and Chromium. `app:check` requires this lane. Docker, Chromium and
anonymous network access to the pinned GitHub release assets are prerequisites;
missing prerequisites and exhausted gateway retries fail the lane.

The immutable version/digest inventory is `app/src/test/resources/released-modules.properties`.
The test downloads real public artifacts, checks each SHA-256, installs through
the administrator's Modules upload form, and restarts the distributable. It never
injects module implementation classes or modifies an existing installation.

Coverage inventory:

| Entry point | Observable assertion |
| --- | --- |
| Login and account administration | Initial-password rotation; MEMBER denied module/admin access; anonymous upload without CSRF denied (403); anonymous protected-page request redirected to login |
| Modules upload | Malformed JAR rejected; eleven pinned release artifacts installed through browser forms |
| Catalog discovery and installation | Default locator URL, immutable source-index resolution, invalid metadata and corrupt artifact rejection, MEMBER preparation denied, prepared request denied, real released Time JAR prepared and approved through browser forms; module is present and usable after restart |
| Runtime restart | Every installed release must appear in the actual runtime registry; missing modules fail even when other journeys succeed |
| Jobs and Dashboard | Owned persisted report downloads exact saved content; real PostgreSQL scheduled work and report survive every packaged-host restart; a different account sees neither and cannot download the report |
| Filesystem configuration | Browser save/start; tool reads a disposable fixture; saved root survives restarts |
| Time tool | Successful UTC call and unsuccessful invalid-zone operation result |
| Shopping tool approvals | Explicit deny leaves no item; explicit allow persists the item |
| Shopping module page | Browser observes item, marks it bought, and observes persisted removal after restart |
| Local-control profile | Browser module actions continue; dev-only operator REST is absent (404) |

The dev profile supplies the documented operator tool/approval entry points.
Subsequent browser and persistence journeys use local-control with the operator
REST controllers absent. No live Telegram, Brave, Docker Engine or external cloud
provider operation is claimed. The filesystem fixture is read-only and isolated.

Logs for every host generation, failure HTML/screenshots, release download retry
diagnostics, the fixture workspace and shopping SQLite database are retained under
`app/build/reports/released-module-acceptance/<run-id>/`; CI retains build reports.
Generated reports must not be committed.

Module repositories separately verify their built JARs through the released
`module-api-test` contract kit. Those in-process results are not packaged-host
acceptance. The legacy module compatibility lane remains separate from this
current public-SDK release fleet.

The catalog journey uses a disposable WireMock HTTPS browser proxy with an
isolated test certificate for GitHub locator/index endpoints. Host URL and
hostname validation remain enabled. Artifact bytes come from the real pinned
public release. This verifies packaged-host discovery through deterministic
metadata fixtures; it does not establish availability of public-default indexes.
