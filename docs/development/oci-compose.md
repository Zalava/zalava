# PostgreSQL Compose deployment

These are runtime-only manifests. Build the application with Gradle Buildpacks;
Compose has no `build:` section. Docker Compose **2.24.4+**, Docker access and
network access for immutable image/artifact pulls are required. The default
app runs as UID/GID 1002:1001 with dropped capabilities. PostgreSQL is version
and digest pinned. Only the application port is published, on host loopback.

## Start a fresh deployment

From the repository root, build and verify a retained local image:

```sh
./gradlew :app:ociImageAcceptanceTest -Poci.image=zalava:local
# Full-JRE compatibility fallback: add -Poci.jlink=false
cp .env.example .env
mkdir -m 0700 .secrets
(
  umask 077
  openssl rand -hex 32 > .secrets/db-admin-password
  openssl rand -hex 32 > .secrets/db-app-password
  printf '%s' administrator > .secrets/bootstrap-login
  openssl rand -hex 32 > .secrets/bootstrap-password
)
chmod 0444 .secrets/*
docker compose config --quiet
docker compose up -d --wait
docker compose ps
```

Open `http://127.0.0.1:8080/login`, using the bootstrap login/password files.
Keep `.env`, `.secrets/` and backups outside Git (root defaults are ignored).
Secret files are readable inside the relevant container, while their host parent
directory is private. Compose file-backed secrets preserve bind-source ownership
and mode: declaring a target UID/mode cannot fix an unreadable source file. Use
an equivalent orchestrator secret mechanism in production. `.env` provides
interpolation and contains file paths/settings; it is not a secret store.

`postgres-data` persists PostgreSQL 18 beneath `/var/lib/postgresql`; mounting
only the old `/var/lib/postgresql/data` location is incorrect for this image.
The init script creates the `zalava` application role without superuser,
role-creation or database-creation rights. The app role owns only its database
and schema so migrations can run. The separate PostgreSQL admin secret is never
mounted into the app. Initialization runs only for an empty database volume;
changing a password file later does not rotate an existing role password.

`zalava-state` persists `/data` (workspace and host/module state). A one-shot
`state-init` service uses the pinned PostgreSQL image solely to set the two app
volume-root owners/modes; it has no network and only ownership capabilities.
It does not recurse through or migrate existing contents. The app depends on
successful initialization and a healthy database. `zalava-diagnostics` is empty
by default; diagnostic collection requires explicit opt-in.

## Resources, readiness and secrets

Default app limits are 2 GiB and 2 CPUs; PostgreSQL has 1 GiB and 1 CPU. These
are actual Compose `mem_limit`/`cpus` settings verified at runtime, not unverified
Swarm-only declarations. Override `ZALAVA_MEMORY`, `ZALAVA_CPUS` and
`ZALAVA_PORT` in your private environment. The app memory calculator reserves
15% headroom with a 50-thread budget; measure the real module workload before
tuning. See [runtime configuration](oci-runtime.md) for precedence and diagnostics.

The Gradle image includes pinned Paketo Health Checker/Tiny Health Checker.
`/workspace/health-check` queries local management readiness, including the
database. No shell or curl is added to the app image. Database disconnects can
make readiness fail and recover without rebuilding/restarting the app.
Management and PostgreSQL ports are not published. The database network is
internal; the separate app network allows module/provider outbound access.

Logs rotate at 3 × 10 MB per service. Graceful shutdown allows 30 seconds, and
failure restarts are limited to five retries. Readiness failure alone does not
restart a Docker container. Heap dumps/JFR/GC files are disabled by default;
enable them only with protected writable diagnostics and operator-enforced
filesystem quota/retention. A named volume alone supplies no portable disk cap.
Do not add compact-header flags to an AOT image without matching its training.

After initial provisioning, follow the account-management contract and remove
bootstrap credential material when it is no longer needed. Keep required secret
mounts consistent with the selected deployment configuration; replacing a file
with a newly generated bootstrap password does not reset an existing account.

## External PostgreSQL

Provision the database and a non-superuser app role through your database
operator, put its password in the configured app password file, then set:

```sh
export ZALAVA_EXTERNAL_DATABASE_URL='jdbc:postgresql://db.example:5432/zalava?sslmode=verify-full'
export ZALAVA_EXTERNAL_DATABASE_USER=zalava
docker compose -f compose.yaml -f compose.external-db.yaml config --quiet
docker compose -f compose.yaml -f compose.external-db.yaml up -d --wait
```

The override removes the app's bundled-DB dependency and puts PostgreSQL behind
an unselected profile. It retains state initialization and app secrets. The
application image is identical. Configure trusted CA material as required by
the database; do not disable certificate checking to make external access work.
Use the same `-f` files for subsequent logs/stop/down commands. The app must be
able to resolve and reach the external database from its container network;
`localhost` refers to that app container.

## Operate, back up and restore

```sh
docker compose logs --tail 200 zalava postgres
docker compose restart zalava
docker compose stop --timeout 30
docker compose up -d --wait
# Removes containers/networks and preserves all named data volumes:
docker compose down
```

For the bundled database, use a protected backup directory and preserve the
workspace volume as a separate, consistent backup. Stop writes when capturing
a matched database/workspace recovery point:

```sh
mkdir -m 0700 backups
umask 077
docker compose stop zalava
docker compose exec -T postgres pg_dump --username postgres --dbname zalava --format custom > backups/zalava.dump
docker compose run --rm --no-deps --user 1002:1001 --entrypoint tar state-init -C /state -cf - . > backups/state.tar
docker compose up -d --wait
```

Restore into fresh target volumes in an isolated project, with the app stopped.
The tar command overlays files; use an empty target to avoid retaining files
created after the snapshot. Preserve the original deployment/backup until recovery
is verified. The corresponding restore commands are:

```sh
docker compose stop zalava
docker compose exec -T postgres pg_restore --username postgres --dbname zalava --clean --if-exists < backups/zalava.dump
docker compose run --rm --no-deps --user 1002:1001 --entrypoint tar state-init -C /state -xf - < backups/state.tar
docker compose up -d --wait
```

Backups contain private data: protect access, encrypt exports, define retention
and test restoration. Use your database provider's backup/recovery controls for
external PostgreSQL. This example does not establish point-in-time recovery.

For app upgrades, retain the current immutable image identity and matched
backup, verify the replacement image/released modules, update `ZALAVA_IMAGE`,
and run `docker compose up -d --no-deps --wait zalava`. Pull a remote image first
when appropriate. Review migrations before rollback; an older application is
not guaranteed to read a migrated database. PostgreSQL major upgrades require
an explicit `pg_upgrade` or dump/restore procedure; changing the image tag alone
does not upgrade the data. Do not replace existing volumes during upgrades.

Only disposable test projects should use
`docker compose --project-name <test-project> down --volumes --remove-orphans`:
that explicitly deletes the project's data volumes. Production teardown and
backup/upgrade operations remain operator actions.

## Verification

`./gradlew :app:ociComposeAcceptanceTest` builds an image and starts a unique
disposable Compose project. It checks fresh initialization, authenticated
browser use/invalid login, app role restrictions, actual limits/image identity,
app/database recreation, transient DB readiness recovery, backup/restore and
graceful stop. It also runs the immutable released-fleet browser/permission/tool
and native SQLite persistence journey through Compose with an external database
fixture. The latter uses a test-only host-network override to reach local
scripted services; deployment defaults retain private bridge networking.

The required pinned-image `check` gate includes this lane and fails when Docker,
Compose or released artifacts are missing. UUID-owned projects are removed with
their volumes; logs/screenshots remain under `app/build/reports/oci-image` and
`released-module-acceptance`. No running installation is changed. Current
released shopping-list coverage uses its actual SQLite storage; final
PostgreSQL-module acceptance is a separate gate. No multi-platform or published
registry image claim follows from local amd64 execution.

References: [Compose secrets](https://docs.docker.com/compose/how-tos/use-secrets/),
[Compose overrides](https://docs.docker.com/reference/compose-file/merge/),
[PostgreSQL image](https://hub.docker.com/_/postgres),
[Paketo health checker](https://github.com/paketo-buildpacks/health-checker).
