# BootUI for local Zalava development

[BootUI](https://github.com/jdubois/boot-ui) is an optional, embedded Spring Boot developer console. It is **not** a Zalava module, is not enabled in production, and is not part of the standard installation.

## Prerequisites and scope

BootUI's current setup documents Java 17+ and Spring Boot 4.x with Spring MVC or WebFlux. Verify the actual Zalava host runtime and Gradle module before installing. This repository may be a documentation-only seed until the application build is present.

## Local setup

1. In the Gradle project containing Zalava's Spring Boot application, add the following dependency **for local development only** (BootUI setup currently documents 2.1.0):

   ```kotlin
   runtimeOnly("com.julien-dubois.bootui:bootui-spring-boot-starter:2.1.0")
   ```

   This simple example is suitable only for a disposable local build. For a committed build change, create a dedicated dev-only dependency configuration or equivalent build variant so the starter is excluded from production bootJar, bootBuildImage and published artifacts. Verify the exclusion by inspecting resolved dependencies and artifacts.

2. Start the app with its existing local development profile (BootUI automatically activates for `dev` or `local`):

   ```bash
   ./gradlew :app:bootRun --args='--spring.profiles.active=dev'
   ```

   Adjust the Gradle task path to the actual application project; preserve any other required profiles and configuration.

3. Open `http://localhost:8080/bootui`, adjusting for the configured server port/context path. BootUI's API is normally under `/bootui/api/**`.

4. Confirm the console displays runtime data, that Zalava's normal routes and database continue working, and that BootUI is absent from production artifacts and disabled without an allowed development profile.

## Security

BootUI provides powerful diagnostic capabilities and may expose sensitive configuration or data. Restrict the **entire Zalava application listener** to loopback when testing locally, or use an equivalently isolated development environment. Do not assume BootUI itself has a separate listener. Do not publish its UI/API/MCP endpoints via a public reverse proxy or shared Docker port. Never enable BootUI in production. Check BootUI's [activation and safety guidance](https://github.com/jdubois/boot-ui/blob/main/docs/setup/activation.md) and [Docker guidance](https://github.com/jdubois/boot-ui/blob/main/docs/setup/environments.md) before containerizing.

## Troubleshooting

- No console: check dependency resolution, development profile, BootUI activation, web stack and path.
- Startup failure: check Spring Boot and BootUI compatibility and conflicts with existing endpoints.
- Docker: loopback binding inside the container differs from host port publishing; validate actual reachability from outside the host.
- AOT/Java agent: do not enable optional instrumentation by default; test separately.

The private maintainer installation and verification plan lives in `cordin/zalava-dev`. A future dynamic module or developer image is explicitly out of scope.

Upstream references: [setup](https://github.com/jdubois/boot-ui/blob/main/docs/SETUP.md), [limitations](https://github.com/jdubois/boot-ui/blob/main/docs/KNOWN-LIMITATIONS.md).
