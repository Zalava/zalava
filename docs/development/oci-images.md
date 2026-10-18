# OCI images

Build release images with Spring Boot's Gradle `bootBuildImage` task. Docker
provides the daemon; no application Dockerfile pipeline is maintained. The
builder and run image are pinned by digest in `ci/oci/builder.txt` and
`ci/oci/run-image.txt`. Update both through reviewed builds and scans. The noble
tiny runtime has no shell and runs as a non-root user.

## Minimal compatible base

Noble Tiny is the selected distroless-like, non-root glibc runtime. Compare
base-image bytes separately from the contributed JRE, application dependencies
and AOT cache. On Linux amd64, the evaluated maintained run images contain:

| Base | Uncompressed local image bytes | Digest prefix |
| --- | ---: | --- |
| Noble Tiny (selected) | 24,870,190 | `9c140f34fd24` |
| Jammy Tiny | 25,268,909 | `fb6c366343f0` |
| Noble standard | 116,057,545 | `577357bc2a59` |

These are local image sizes, not compressed registry transfer sizes or a claim
of a global minimum across every distribution. The selected Tiny family is
tested with TLS, crypto, JFR and real released modules, including native SQLite.
Jammy requires its matching builder/stack and provides no size advantage here.
Standard Noble is an option when a module requires its additional OS libraries;
it is larger and has not passed this acceptance lane. Arbitrary scratch, Alpine
or unrelated distroless images cannot replace the CNB run image without proving
stack/ABI compatibility. Native module requirements remain part of acceptance.

The larger measured reductions come from the JVM/application layout: about
800 MB for full JRE/AOT, 504 MB for full JRE/sliced and 330 MB for jlink/sliced.
These single-host candidate measurements do not replace per-release validation.
The full JRE/AOT default preserves the existing startup and independent-module
contract; the smaller variants remain explicit build options.

Paketo documents [available builders](https://paketo.io/docs/reference/builders-reference/)
and [stack selection and maintenance](https://paketo.io/docs/concepts/stacks/).
Keep digest updates reviewed and scanned; a small maintained base can still have
known vulnerabilities. Base-size selection does not establish a clean scan.

## Build and verification

```sh
# Build a named local image using an isolated PostgreSQL training database:
./gradlew :app:leydenImageIntegrationTest -Pleyden.image-name=zalava:local
# Build and exercise the real OCI image through the released-module browser lane:
./gradlew :app:ociImageAcceptanceTest -Poci.image=zalava:verified
```

The image task requires Java 25, Docker access and network access to the pinned
builder's dependencies. Full OCI acceptance also requires the repository's
Playwright runtime and immutable released module artifacts. `check` includes
OCI acceptance; missing prerequisites fail. Use the pinned CI verification image
for the complete local gate before pushing. Reports are retained beneath
`app/build/reports/oci-image` and `released-module-acceptance`.

The build-only command preserves the operator image-build interface and does not
start a browser. Supplying an image name retains that local image. Test-owned
databases, networks and CNB caches are disposable; acceptance does not publish
images or change an installation. Direct `bootBuildImage` callers must supply
the `leyden.training.network`, `.jdbc-url`, `.username` and `.password` Gradle
properties when AOT training is enabled. Training uses disposable credentials;
never pass deployment/provider credentials at build time.

## Layout, JVM and caches

The executable archive explicitly contains the standard dependencies, loader,
snapshot-dependencies and application layers. JVM AOT cache training is enabled
by default with a full maintained Java 25 JRE. Spring AOT processing and native
images remain disabled so modules can be loaded dynamically after installation.

The pinned Paketo Spring Boot buildpack currently disables layer-index slicing
when using its extracted AOT layout. The image therefore retains a combined
application/AOT layer; an app-only change does not imply an app-only transfer.
`-Poci.aot=false` selects the supported sliced layout for comparisons, with a
startup tradeoff. Neither mode guarantees byte-identical trained cache files.

Ordinary local builds reuse `zalava-local-build-cache` and
`zalava-local-launch-cache`. Use `-Poci.cache-prefix=<stable-owner-name>` for
distinct build owners, and persist the named Docker volumes on repeat builds.
Ephemeral CI runners start with cold daemon caches; cross-run cache export is
not configured or claimed. The verification lane uses UUID-scoped caches and
removes only its own resources.

`-Poci.jlink=true -Poci.aot=false` evaluates Paketo's supported jlink integration. It retains
Java SE plus explicit crypto, JFR/management, HTTP server, security, DNS,
filesystem, charset and locale modules required by the host and verification
fixtures. This opt-in variant must pass the same real-image acceptance lane.
The pinned buildpack currently creates a jlink AOT cache that is rejected after
its runtime module image changes. `oci.jlink=true` with `oci.aot=true` is rejected
before the image build; functional success alone does not prove cache use.
The full JRE remains the supported default for independently installed modules;
new modules may require additional JDK modules. A flag or successful build alone
does not prove reflection, native-library, TLS or module compatibility.

Runtime deployment/diagnostics configuration and Compose operation are documented
separately as those contracts are delivered. Local test images are not published
release artifacts. Multi-platform support requires execution evidence for each
platform; a multi-platform builder manifest alone does not establish support.
