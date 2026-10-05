# Reusable browser acceptance image

The CI image contains Java 25, Node 22/npm, Chromium headless shell, FFmpeg,
and native browser libraries. Gradle still comes from the repository wrapper.
This is a build/test tool image only. The application release image remains
`ghcr.io/zalava/zalava`, produced independently by `bootBuildImage` using its
existing buildpack configuration. It does not inherit or copy the CI image,
browsers, npm or test dependencies.
The Dockerfile bases are pinned by digest. The Playwright version is read from
the project's Java dependency in `build.gradle`; the Gradle installer rejects
an incompatible image version.

`Build and verify CI image` publishes candidates to `ghcr.io/zalava/ci-playwright`
when the image inputs change on main, every Monday, or via **Run workflow**.
Its second job pulls the candidate on a fresh runner and executes the complete
host/API/contract-kit gate, including the real-distributable browser lane.
The first candidate is also built on `step-ci-image-*` branches for bootstrap.
Image publication requires the repository's Actions token to have package
write access; image consumers need package read access or a public package.

After a successful trial, copy the immutable `ghcr.io/zalava/ci-playwright@sha256:…`
reference from the build job summary into `ci/playwright/image.txt` in a PR.
Regular verification pulls that digest; it never rebuilds the image. Failed
candidates do not affect the pinned image. Compare the trial's pull plus full
verification duration with regular CI before promoting a candidate.

For a Playwright upgrade, change the Java dependency, generate a matching
candidate from that branch, and update the image pin in the same PR. For a
Java/Node/base-image update, update the Dockerfile tag and digest and use the
same trial/promotion process. Scheduled rebuilds refresh native packages;
pinned Java/Node bases change only through a reviewed Dockerfile update.

Local trial:

```sh
docker build --build-arg PLAYWRIGHT_VERSION=1.61.0 -t zalava-ci-trial ci/playwright
bash ci/playwright/verify-in-image.sh zalava-ci-trial
```

The runner passes its normal Gradle home (or explicit `GRADLE_USER_HOME`),
uses matching absolute checkout paths, and shares the host network and Docker
socket so Testcontainers can reach mapped PostgreSQL ports. It runs as the
runner's UID, keeping build outputs and cached files owned by the runner.
