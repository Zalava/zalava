# Contribute to Zalava

Read [repository guidance](../AGENTS.md), the architecture guide, and relevant documentation. Keep behavior, tests, and public documentation aligned. Use the Gradle wrapper, run focused tests during development, and run the documented verification task before a pull request.

Add regression tests for defects and use the real product acceptance lane for user-facing behavior when available. Do not add secrets, private URLs, developer-machine paths, user data, or generated build/browser output. Explain Module API compatibility effects and preserve external identifiers unless a compatibility break is reviewed.

## Fast local feedback

Run commands from the repository root using Java 25 and the checked-in wrapper.
Keep the normal Gradle user home (or an explicitly configured `GRADLE_USER_HOME`)
so dependency downloads and incremental work can be reused. Configuration caching,
build caching and project parallelism are already enabled. Avoid routine `clean`,
`--rerun-tasks` and `--no-build-cache` during editing.

Start with the affected test class, for example:

```sh
./gradlew :app:test --tests org.zalava.packaging.OciAcceptanceJourneysTest
```

For Module API or contract-kit changes, target that project's tests instead:

```sh
./gradlew :module-api:test --tests org.zalava.api.ModuleApiContractsTest
./gradlew :module-api-test:test
```

Replace the test selector with the actual affected class; a no-matching-tests
failure is not successful verification. At a broader local checkpoint, run:

```sh
./gradlew spotlessCheck :module-api:test :module-api-test:check :app:test :app:architectureTest
```

This checkpoint does not execute the browser, released-module or OCI acceptance
lanes, and does not replace the full gate. Full-context MockMvc tests remain the
required HTTP regression layer. If a focused run leaves partial coverage data,
rerun the complete affected test task with `--rerun-tasks` before checking its
coverage threshold.

Before pushing, execute the complete pinned-image gate required by `AGENTS.md`:

```sh
CI=true GITHUB_ACTIONS=true bash ci/playwright/verify-in-image.sh "$(cat ci/playwright/image.txt)" --rerun-tasks --no-build-cache
```

Docker and Compose must be available. This gate intentionally reexecutes tasks;
focused checks and cached results do not satisfy it. It includes formatting,
architecture, coverage, browser and packaged JAR/OCI acceptance. Do not skip
missing prerequisites or lower coverage to shorten it.

## Measuring build performance

The pinned-image wrapper already adds `--profile`. For another Gradle command,
add that flag explicitly. Reports appear in `build/reports/profile/` and include
configuration, dependency resolution and task durations. Test XML in
`app/build/test-results/<task>/` identifies slow tests in each lane. These test
durations do not necessarily include all fixture and Spring context setup.

Packaged-host and OCI acceptance also append `timings.tsv` under their existing
UUID-owned report directories in `app/build/reports/released-module-acceptance/`
and `app/build/reports/oci-image/`. Each headerless row contains a phase label,
`passed` or `failed`, and elapsed **nanoseconds** measured with a monotonic clock.
Rows are written on failure too; writing diagnostics never replaces a test failure.
CI already uploads these directories with the verification reports.

Phases distinguish artifact downloads (including retries and digest checking),
database startup, host startup/shutdown, image building, JVM probes, and Docker
and Compose journeys. Repeated host phases expose restart cost; the broader
journey and Gradle task durations provide the remaining browser/fixture context.
Phases can nest and run concurrently, so do not sum them as elapsed build time.

Compare the same revision, commands, runner resources and cache conditions.
Measure cold and warm builds separately, keep failures separate from passing
baselines, and repeat representative runs before attributing an improvement.
