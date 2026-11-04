# BootUI for local Zalava development

[BootUI](https://github.com/jdubois/boot-ui) is an optional embedded developer
console. Zalava's `:app:bootuiRun` task adds the pinned **2.1.0** starter to a
separate runtime. The host uses **Java 25, Spring Boot 4.1.0 and Spring MVC**;
BootUI requires Java 17+ and Spring Boot 4.x. Use the repository Gradle wrapper,
Java 25 and Node/npm, as for normal `:app:bootRun` development.

## Start a local development instance

Use a development checkout, a separate PostgreSQL database and a disposable
workspace. Keep the managed installation's database, credentials and workspace
separate. This example starts a temporary database with local test credentials:

```bash
docker run --rm -d --name zalava-bootui-db \
  -p 127.0.0.1:15432:5432 \
  -e POSTGRES_DB=zalava_bootui -e POSTGRES_USER=bootui \
  -e POSTGRES_PASSWORD=bootui-local-test postgres:18.4-alpine
docker exec zalava-bootui-db pg_isready -U bootui -d zalava_bootui
```

Wait for `pg_isready` to report that it accepts connections. In the repository
root, configure this development instance and start it:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:15432/zalava_bootui
export SPRING_DATASOURCE_USERNAME=bootui
export SPRING_DATASOURCE_PASSWORD=bootui-local-test
export AGENT_WORKSPACE=file:./.bootui/workspace/
export ZALAVA_ACCOUNTS_BOOTSTRAP_LOGIN=bootui-admin
read -rs -p 'Disposable administrator password (at least 12 characters): ' ZALAVA_ACCOUNTS_BOOTSTRAP_PASSWORD
echo
export ZALAVA_ACCOUNTS_BOOTSTRAP_PASSWORD
./gradlew :app:bootuiRun
```

Open [BootUI](http://127.0.0.1:18080/bootui) and
[Zalava](http://127.0.0.1:18080/). Complete Zalava's normal login/onboarding using
the `bootui-admin` account. Configure a development model provider if testing chat;
BootUI diagnostics do not require one.

The task includes the `local` Spring profile without replacing active profiles.
Preserve any other required profiles and configuration with `--args`, for example:

```bash
./gradlew :app:bootuiRun --args='--spring.profiles.active=dev --server.port=18090 --management.server.port=18092'
```

Use separate ports from other running hosts. The defaults are `18080` for the
application and `18082` for management. BootUI uses the application listener,
normally `/bootui` and `/bootui/api/**`; it has no separate HTTP port.

## Local access and isolation

`bootuiRun` fixes the application and management addresses to `127.0.0.1`,
disables the separate JobRunr dashboard, DevTools restart/LiveReload and BootUI
MCP, and keeps BootUI in `AUTO` activation mode with remote access disabled.
The task rejects command-line overrides of its fixed listener/BootUI settings.
Production profiles (`prod` or `production`) keep BootUI dormant. Do not proxy
these listeners or reuse production configuration/data for this development run.
BootUI's local console can inspect and change development state; secret masking
is not a substitute for a separate development database.

BootUI is declared only in `bootuiRuntime`. It is absent from normal `bootRun`,
the host's `runtimeClasspath`, test runtimes, `bootJar`, `bootBuildImage` and
standard images built from that archive. No project property can add it to
release packaging. The task does not change the managed service, pinned release
update path, module API, Java agent or AOT/Leyden image configuration.

BootUI stores local overrides under `.bootui/`, which Git ignores. Keep this
directory private. A custom `BOOTUI_OVERRIDES_FILE` must also point outside
tracked source files.

## Verify the setup

```bash
./gradlew :app:verifyBootuiIsolation
./gradlew :app:dependencies --configuration bootuiRuntime
./gradlew :app:dependencies --configuration runtimeClasspath
```

`verifyBootuiIsolation` is part of `:app:check`. It resolves the starter, checks
that only `bootuiRun` includes it, checks the ordinary/test dependency graphs and
executable archive, and verifies that `bootBuildImage` consumes that archive.
It does not build or run an OCI image.

With the development instance running:

```bash
python3 scripts/verify-bootui.py
```

This checks real UI/API responses, local activation, database/management health,
Zalava's rendered login form and rejection of a non-local Host header. For custom
ports, pass `--url` and `--management-url` with loopback URLs. Then inspect the UI:

1. Open BootUI's overview, health, PostgreSQL and Flyway panels. Confirm runtime
   data and the development database/migrations where the upstream panel supports
   them; unavailable optional instrumentation is not installation failure.
2. Log in to Zalava, complete onboarding and exercise normal development flows.
   Verify chat separately with a configured development provider.
3. Inspect listening sockets: application and management must bind to loopback,
   with no additional JobRunr or LiveReload listener. Confirm a non-loopback
   address cannot connect to either port.
4. Stop this process and use normal `:app:bootRun` (or the production archive)
   with the same disposable database. BootUI must be absent even with a `local`
   profile. The repository's pinned CI-image gate remains required before push.

## Stop and roll back

Stop `bootuiRun` with Ctrl-C. Start the usual `:app:bootRun` to run without
BootUI; no dependency edit or managed service restart is needed. Stop the
example database with `docker stop zalava-bootui-db`; `--rm` removes its
disposable container and anonymous data volume. Delete `.bootui/` only when its
local overrides/workspace are no longer needed, and unset the exported
development variables before running another instance.

## Troubleshooting

- **Dependency resolution:** confirm Maven Central access and the pinned
  `bootuiRuntime` dependency report. The starter is resolved only by the BootUI
  task or its isolation check.
- **No console:** use `bootuiRun`, check the URL/port/context path, active profiles
  and the startup log. `prod`/`production` intentionally disables BootUI.
- **Database/startup failure:** check the disposable PostgreSQL readiness and
  datasource variables. Existing managed configuration is not loaded for you.
- **Address already in use:** choose unused application and management ports.
- **Unavailable panel:** check upstream compatibility and optional capabilities;
  this setup installs neither the BootUI Java agent nor MCP diagnostics.

Upstream references: [setup](https://github.com/jdubois/boot-ui/blob/main/docs/SETUP.md),
[activation](https://github.com/jdubois/boot-ui/blob/main/docs/setup/activation.md),
[properties](https://github.com/jdubois/boot-ui/blob/main/docs/PROPERTIES.md),
[limitations](https://github.com/jdubois/boot-ui/blob/main/docs/KNOWN-LIMITATIONS.md).
