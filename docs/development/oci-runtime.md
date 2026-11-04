# OCI runtime configuration

One Gradle-built image runs in local, CI, home and production environments.
Application configuration belongs at launch. Image building never receives
deployment credentials or real user data. See [OCI images](oci-images.md).

## Configuration contract

| Setting | Phase / default | Example or purpose |
| --- | --- | --- |
| `BP_JVM_VERSION` | Build / Java 25 | Change through reviewed Gradle configuration |
| `oci.aot`, `oci.jlink` | Gradle build / true, true | Archive/JRE variants; not runtime environment variables |
| `oci.cache-prefix` | Gradle build / `zalava-local` | Stable CNB cache owner |
| `JAVA_TOOL_OPTIONS` | Runtime / buildpack contributions | JVM flags and `-D` application properties; avoid secrets because Java prints this value |
| `BPE_*` | Build-time environment-variables buildpack inputs | Baking values requires that buildpack; no custom BPE policy is configured here. Prefer runtime settings |
| `BPL_JVM_HEAD_ROOM` | Runtime / buildpack default 0% | `15` reserves additional native/process headroom |
| `BPL_JVM_THREAD_COUNT` | Runtime / buildpack default 250 | `50` changes the memory budget, not a thread concurrency limit |
| `BPL_JAVA_NMT_ENABLED` | Runtime / true | `false` disables native tracking overhead/output |
| `BPL_JAVA_NMT_LEVEL` | Runtime / summary | `detail` only when diagnosing |
| `BPL_JFR_ENABLED` / `BPL_JFR_ARGS` | Runtime / false | Opt-in bounded recordings; example below |
| `BPL_HEAP_DUMP_PATH` | Runtime / unset | Opt-in writable dump destination; no heap dump by default |
| `SPRING_DATASOURCE_URL` | Runtime / required PostgreSQL | `jdbc:postgresql://postgres:5432/zalava` |
| `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` | Runtime / deployment supplied | Prefer mounted `spring.datasource.username` / `.password` configtree files |
| `SPRING_CONFIG_IMPORT` | Runtime / packaged optional private config | `configtree:/run/secrets/` requires mounted files; omit `optional:` when missing secrets must fail |
| `SPRING_PROFILES_ACTIVE` | Runtime / default | Explicit deployment profiles; do not use test-only profiles in production |
| `SERVER_PORT` / `SERVER_ADDRESS` | Runtime / 8080, all interfaces | Container port and bind address |
| `MANAGEMENT_SERVER_PORT` | Runtime / 8082 | Readiness `/actuator/health/readiness`; keep management private |
| `AGENT_WORKSPACE` | Runtime / `file:./workspace/` | `file:/data/workspace/`; persist host-owned module/configuration/workspace state |
| `ZALAVA_ACCOUNTS_BOOTSTRAP_LOGIN` / `ZALAVA_ACCOUNTS_BOOTSTRAP_PASSWORD` | Runtime / deployment supplied for bootstrap | Use protected configtree keys; remove bootstrap credentials after initial provisioning |
| `ZALAVA_DISCOVERY_REMOTE_ENABLED` | Runtime / false | Explicit remote module discovery; install immutable module artifacts through the authenticated module UI |
| `ZALAVA_OBSERVABILITY_ENABLED` | Runtime / false | Enables Prometheus export when configured |
| `JOBRUNR_DASHBOARD_ENABLED` | Runtime / true | Set `false` unless deliberately exposing a protected dashboard |
| `LOGGING_LEVEL_ROOT` | Runtime / INFO | Log to stdout; collector owns bounded retention |

Module-specific configuration and persisted module settings remain owned by
their module UI/contracts. Changing environment variables does not rewrite
already persisted module configuration. Database persistence and workspace
persistence are both required; a container filesystem is disposable.

For ordinary Spring settings the relevant precedence, low to high, is packaged
and imported configuration (including configtree), OS environment, Java system
properties, `SPRING_APPLICATION_JSON`, command-line arguments. Keep each setting
in one source to avoid surprises. `_FILE` is not a general Spring Boot feature:
use configtree property filenames. PostgreSQL's own `_FILE` settings are separate.
Do not put passwords in command-line arguments or `JAVA_TOOL_OPTIONS`. Runtime
environment is visible to daemon administrators; configtree improves handling
but is not encryption or protection from the host administrator.

## Quotas and JVM options

Set real container memory/CPU limits in every environment. The buildpack memory
calculator subtracts metaspace, code cache, direct memory, thread stacks and
headroom from the cgroup memory limit to determine heap. Start with 2 GiB / 2 CPU,
`BPL_JVM_HEAD_ROOM=15` and a measured thread-count budget, then tune from actual
host/module workloads. This is a starting point, not a minimum-memory promise.
Avoid fixed `-Xmx` alongside the calculator unless deliberately replacing its
budget; increasing heap does not increase container native headroom. Java 25
container ergonomics determines available processors from CPU quota. Fractional
CPU quotas are rounded for JVM processor count; they still throttle scheduling.

Compact object headers remain disabled by default. The actual Java 25 runtime
accepts `-XX:+UseCompactObjectHeaders`; enabling it changes archive compatibility
and requires matching training/runtime options for AOT images. Use a verified
no-AOT variant for isolated evaluation. Never copy experimental/unrecognized JVM
flags into production or suppress JVM option errors. Unsupported options fail
startup. A successful startup alone does not prove that an AOT cache was used.

## Diagnostics and failure behavior

The contributed JRE adds `-XX:+ExitOnOutOfMemoryError`. Java heap exhaustion exits
the process (verified exit 3). Heap dumps are disabled by default and can contain
credentials, messages and user data. Opt in for a controlled diagnostic window:

```sh
# Private, writable mount with an operator-enforced filesystem quota:
# directory owner must match the container UID; mode 0700, never a public asset path.
BPL_HEAP_DUMP_PATH=/diagnostics
BPL_JFR_ENABLED=true
BPL_JFR_ARGS=filename=/diagnostics/session.jfr,maxsize=32m,maxage=5m,dumponexit=true
JAVA_TOOL_OPTIONS=-Xlog:gc*:file=/diagnostics/gc.log:time,level,tags:filecount=3,filesize=5m
```

JFR and GC settings bound their own output. Heap dump size is workload/heap
dependent: the mounted filesystem needs an enforced quota, sufficient free space
and an external retention policy. A Docker named volume alone has no portable
size cap. Keep files private, encrypt diagnostic exports, expire after the
incident (for example 24 hours), and delete dumps once inspected. Do not enable
remote debugging by default. The shell-free JRE image does not promise JDK tools
such as `jcmd`; preserve logs/JFR and use compatible external analysis tooling.

A cgroup OOMKill is different: Docker reports `OOMKilled=true`, usually exit
137, and the kernel may terminate the process before Java writes any dump or
shutdown hook. Do not treat a missing dump as proof no memory failure occurred.
Correlate container state, resource metrics and JVM output. A restart policy can
start a fresh process; it cannot repair undersized quotas or persisted corruption.
Use bounded failure retries during diagnosis, preserve data, and avoid permanent
restart loops. Graceful stops need enough timeout for requests and database work.

Local/CI use isolated ports, database and writable state. Home/production retain
their own database/workspace and read-only secret mounts. Compose and Kubernetes
consume the same image: apply memory/CPU limits, private service networking,
readiness and termination grace periods at the orchestrator boundary. Never
expose database, management or diagnostic ports publicly by default.

The required `ociImageAcceptanceTest` runs the real image's launcher/JVM with
distinct quotas and configuration sources, opt-in diagnostics and separate heap
OOM/OOMKill fixtures, then the actual released-fleet browser journey. Probes use
a disposable fixture JAR and data; they are not production endpoints. Missing
prerequisites fail. Logs/state evidence remain in `app/build/reports/oci-image`;
sensitive heap contents are deleted after assertions.

References: [Spring external configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html),
[Paketo Java runtime options](https://paketo.io/docs/howto/java/),
[memory calculator](https://paketo.io/docs/reference/java-reference/),
[Java 25 options](https://docs.oracle.com/en/java/javase/25/docs/specs/man/java.html).
