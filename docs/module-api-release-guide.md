# Releasing and consuming the Zalava Module API

This guide covers the release boundary between the Zalava core repository and an
independently developed Zalava module repository.

## Publish a new module API version

`module-api` is the stable Java SPI that Zalava supplies when it loads an external
module. It is published as an immutable Maven package from this repository:

```text
org.zalava:module-api:<version>
https://maven.pkg.github.com/Zalava/zalava
```

Before a release, merge the intended SPI change to `main`, verify it, and
choose the next semantic version. Patch versions preserve the contract, minor
versions add compatible APIs, and major versions may break modules.

```bash
GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :module-api:test
git tag -a v<major>.<minor>.<patch>[-<prerelease>] -m "Publish module-api <version>"
git push origin v<major>.<minor>.<patch>[-<prerelease>]
```

Pushing the tag triggers `.github/workflows/publish-module-api.yml` and
`.github/workflows/publish-module-api-test.yml`. Both workflows verify their
module and publish the immutable artifacts at the tag version to GitHub Packages
with the GitHub Actions token. Wait for both workflows to pass before updating
dependent modules. A published version is never replaced; make a new version for
any correction.

The tag version is owned by the release owner and is never chosen by automation.
Because the kit and the SPI share one repository version, a correction to either
artifact must use a new tag version; never re-point an existing tag.

## Publish a new contract kit version

`module-api-test` is released with `module-api` at the same version. Tag the
reviewed `main` commit and let `publish-module-api-test.yml` publish the kit,
then confirm the release is consumable from GitHub Packages (this resolves the
kit POM, its `module-api` dependency, and both JARs):

```bash
scripts/verify-module-api-test-release.sh <major>.<minor>.<patch>
```

The check reads `GITHUB_ACTOR` and `ZALAVA_PUBLISH_TOKEN`, or `gpr.user`/`gpr.key` from
`~/.gradle/gradle.properties`. A module repository should pin only a kit version
whose `module-api` SPI is compatible with the module's own `module-api` pin.

## Configure a module repository

Use a released SPI in normal module development. Do not use `mavenLocal()` as
the everyday dependency source; reserve it for an unreleased, coordinated SPI
change.

For projects that use `FAIL_ON_PROJECT_REPOS`, configure the package repository
in `settings.gradle`:

```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven {
            url = uri('https://maven.pkg.github.com/Zalava/zalava')
            credentials(PasswordCredentials) {
                username = providers.gradleProperty('gpr.user')
                    .orElse(providers.environmentVariable('GITHUB_ACTOR')).orNull
                password = providers.gradleProperty('gpr.key')
                    .orElse(providers.environmentVariable('ZALAVA_MODULE_API_TOKEN'))
                    .orElse(providers.environmentVariable('ZALAVA_PUBLISH_TOKEN')).orNull
            }
        }
    }
}
```

Then declare the SPI as `compileOnly` and use it in tests:

```groovy
def moduleApiVersion = providers.gradleProperty('moduleApiVersion').orElse('1.0.0')

dependencies {
    compileOnly "org.zalava:module-api:${moduleApiVersion.get()}"
    testImplementation "org.zalava:module-api:${moduleApiVersion.get()}"
}
```

Do not package `module-api` inside the module JAR. Zalava supplies the SPI at
runtime. Use [`templates/zalava-module`](../templates/zalava-module) as the baseline
for independent module repository structure and publication.

## Test a module with the contract kit

`module-api-test` is the released test kit for external modules. It exercises a
real module artifact at the stable SPI boundary in-process, without booting Zalava:

```text
org.zalava:module-api-test:<version>
https://maven.pkg.github.com/Zalava/zalava
```

Add it as a test dependency; it brings JUnit 5 and AssertJ for module-owned
assertions:

```groovy
dependencies {
    testImplementation "org.zalava:module-api-test:${moduleApiVersion.get()}"
}
```

`ExternalModuleTestHarness` loads the built module artifact under Zalava's isolated
classloader; `ModuleContractKit` then creates providers with the same scoped
configuration and invokes their tools:

```java
try (ModuleContractKit kit =
    ModuleContractKit.load(moduleJar, List.of(runtimeJar), "my-module", "1.0.0")) {
  ConfigFixture config =
      ConfigFixture.empty()
          .factoryConfiguration("my-module", "my-factory", Map.of("region", "eu"));

  try (ProviderFixture providers = kit.providers(config)) {
    SeaOperationResult result =
        providers.invoke("my-provider", "my_tool", JsonNodeFactory.instance.objectNode());
    assertThat(result.success()).isTrue();
  }
}
```

The kit asserts module-owned behavior only. Host-owned resolution, validation,
permissions, approvals, persistence and transport remain Zalava's responsibility and
are covered by Zalava's own tests; a module repository must not encode or bypass
them.

## Authenticate safely

For local development, add a GitHub username and a classic personal access
token with `repo` and `read:packages` to the user Gradle properties file:

```properties
# ~/.gradle/gradle.properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.key=YOUR_PACKAGE_READ_TOKEN
```

Never commit this file or a token.

Official Zalava API artifacts resolve anonymously from the public Maven host.
Module repositories do not need a credential to consume them. Use release
credentials only to publish a repository's own reviewed release assets.

```yaml
- env:
    ZALAVA_MODULE_API_TOKEN: ${{ secrets.ZALAVA_MODULE_API_READ_TOKEN }}
  run: ./gradlew test

- if: startsWith(github.ref, 'refs/tags/v')
  env:
    CI_COMMIT_TAG: ${{ github.ref_name }}
    GITHUB_ACTOR: ${{ github.actor }}
    ZALAVA_PUBLISH_TOKEN: ${{ secrets.ZALAVA_PUBLISH_TOKEN }}
    ZALAVA_MODULE_API_TOKEN: ${{ secrets.ZALAVA_MODULE_API_READ_TOKEN }}
  run: ./gradlew build publish
```

## Release a module

After a module PR passes against the released SPI and is merged, tag its
reviewed default-branch commit as `v<major>.<minor>.<patch>`. Its release
workflow must verify the module, publish its immutable JAR to that module
repository's GitHub Packages registry, and generate a SHA-256 digest. Zalava
installation remains separately approval-, provenance-, compatibility-, and
digest-gated; publishing a module does not enable it automatically.
