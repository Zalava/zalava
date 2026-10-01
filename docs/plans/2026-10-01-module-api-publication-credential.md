# Module API publication credential mapping

## Scope

Repair the tag publication workflows so their GitHub Actions token is exposed through the
`ZALAVA_PUBLISH_TOKEN` environment variable consumed by both Gradle publication projects.

## Evidence and release rule

The `v0.1.0-alpha.2` tag points at public `main` commit `d451d9d8` and its exact
`CI_COMMIT_TAG=v0.1.0-alpha.2 ./gradlew :module-api:test :module-api-test:test` verification
passed. Both tag workflows then failed before publication because the workflows supplied only
`GITHUB_TOKEN`, while Gradle requires `GITHUB_ACTOR` and `ZALAVA_PUBLISH_TOKEN`.

This change maps the existing actions token to that required variable. It does not move, delete,
or reuse `v0.1.0-alpha.2`; after this fix merges, publish a new immutable prerelease tag and
verify both artifacts are resolvable before any external module pins it.

`v0.1.0-alpha.3` is the selected successor. Consumer defaults in the development request,
templates, release guide and generated API projects advance to that coordinate in the release
PR before the tag is created.
