# Zalava

Zalava is an open-source, self-hosted AI assistant that uses large language
models (LLMs) to answer questions, access information and perform tasks through
connected tools and services.

It provides a web interface for interacting with the assistant and managing its
configuration. Multiple users can share an installation, with individual
accounts, conversation histories and configurable permissions.

Zalava does not provide its own language model. It connects to supported
external AI providers or compatible locally hosted models.

Its functionality can be extended through independently developed and released
modules. Modules can provide tools, service integrations, application pages,
communication channels and other capabilities.

Administrators manage the available modules, their configuration, user access
and which operations require explicit confirmation.

## Features

- **Conversational assistant:** interact with language models through a web-based
  chat interface.
- **Model providers:** configure supported cloud-based or locally hosted language
  models; see [supported providers](docs/development/model-providers.md).
- **Tools and integrations:** access information and perform operations through
  installed modules.
- **Scheduled tasks:** configure recurring work and access generated reports.
- **Knowledge:** manage information that can be made available to the assistant.
- **Memory:** manage information retained for future conversations.
- **Skills:** manage reusable instructions and context for the assistant.
- **Multi-user support:** manage individual accounts, roles and permissions.
- **Operation approvals:** control which chat-initiated operations require user
  confirmation, subject to mandatory safeguards.
- **Module management:** discover, install, configure and manage external modules.
- **Application pages:** access dedicated interfaces provided by modules.
- **Monitoring:** inspect application metrics, execution details, tool calls and
  logs.

See [tool access and permissions](docs/development/tool-access.md) for the
distinction between tool access, chat confirmations and module page access.

## Available modules

Official modules are developed in separate repositories in the
[Zalava GitHub organization](https://github.com/Zalava). Examples include web
search (Brave Search), web content retrieval, browser automation, filesystem
access, shopping lists, time, tasks, Telegram, Home Assistant, Docker, MCP
bridging and document processing (Apache Tika).

These are optional integrations, not capabilities included in every
installation. Availability depends on module release compatibility, catalog
configuration, credentials, permissions and any required external services.
Consult the individual module documentation for supported operations and
limitations. Administrators review and approve installations through the
module management interface.

## Getting started

Zalava requires PostgreSQL and access to a supported language model. It can be
run as a Java application or deployed using a container-based setup. Building
from source requires Java 25 and the frontend build prerequisites.

- [Install and run](docs/getting-started/installation.md)
- [Configure](docs/getting-started/configuration.md)
- [Configure model providers](docs/development/model-providers.md)
- [Operate and upgrade](docs/operations.md)
- [Docker Compose](docs/development/oci-compose.md)

## Architecture

Zalava is implemented in Java using Spring Boot and Spring AI, with PostgreSQL
for persistent data.

The host application provides the assistant runtime, user management,
authorization, task scheduling, persistence and web interface.

External JVM modules extend the application through a versioned Module API.
Modules are developed, tested and released independently of the host
application.

The host manages module lifecycle, configuration and policy enforcement.
Modules run within the host's JVM; they should not be treated as isolated
security sandboxes. Only install modules whose code and origin you trust.

See the [architecture documentation](docs/development/architecture.md) and
[module development guide](docs/development/module-development.md).

## Technologies and open-source projects

Zalava is built on a number of open-source projects, frameworks and standards.

### Core platform

- [OpenJDK](https://openjdk.org/) — Java 25 and the JVM runtime.
- [Spring Boot](https://spring.io/projects/spring-boot) — application framework
  and runtime infrastructure.
- [Spring AI](https://spring.io/projects/spring-ai) — language model integration,
  tool calling and AI infrastructure.
- [Spring AI Agent Utils](https://github.com/spring-ai-community/spring-ai-agent-utils)
  — reusable utilities for AI agents.
- [Spring Security](https://spring.io/projects/spring-security) — authentication
  and authorization infrastructure.

### Data and background processing

- [PostgreSQL](https://www.postgresql.org/) — persistent application data.
- [Flyway](https://github.com/flyway/flyway) — database schema migrations.
- [JobRunr](https://www.jobrunr.io/) — background job execution and recurring
  task scheduling.

### User interface

- [assistant-ui](https://www.assistant-ui.com/) — conversational user interface
  components.
- [React](https://react.dev/) — interactive frontend components.
- [jte](https://jte.gg/) — server-side HTML templates.
- [htmx](https://htmx.org/) — interactive server-rendered interfaces.
- [Vite](https://vite.dev/) — frontend build tooling.

### Integration standards

- [Model Context Protocol (MCP)](https://modelcontextprotocol.io/) — integration
  with external tools and services.
- [JSON Schema](https://json-schema.org/) — structured tool inputs and module
  configuration.

### Observability and operations

- [Spring Boot Actuator](https://docs.spring.io/spring-boot/reference/actuator/index.html)
  — application health and operational endpoints.
- [Micrometer](https://micrometer.io/) — application metrics.
- [Prometheus](https://prometheus.io/) — supported metrics collection integration;
  a separate Prometheus installation is optional.
- [Paketo Buildpacks](https://paketo.io/) — OCI container image creation.
- [Docker Compose](https://docs.docker.com/compose/) — container-based deployment.

### Development and testing

- [Gradle](https://gradle.org/) — build and dependency management.
- [JUnit](https://junit.org/) — automated testing.
- [Testcontainers](https://testcontainers.com/) — container-based integration
  testing.
- [Playwright](https://playwright.dev/java/) — browser automation and acceptance
  testing.
- [ArchUnit](https://www.archunit.org/) — automated architecture verification.
- [WireMock](https://wiremock.org/) — HTTP service simulation.
- [JaCoCo](https://www.jacoco.org/jacoco/) — test coverage measurement.
- [axe-core](https://github.com/dequelabs/axe-core) — automated accessibility
  testing.

External Zalava modules may use additional open-source projects, which are
documented in their respective repositories.

Zalava gratefully acknowledges the work of the maintainers and contributors of
these projects.

## Project status and limitations

Zalava is under active development. This README describes the implementation in
the repository, not a guarantee that every feature is included in a stable
published release.

The available functionality depends on the installed modules, the configured
language model and the permissions assigned to each user.

Language models may produce incorrect answers, select inappropriate tools or
fail to complete tasks. Permissions and confirmation mechanisms provide control
over operations but do not eliminate all risks associated with AI or third-party
integrations.

Self-hosting the application does not necessarily mean that all processing
takes place locally. When external AI providers or services are configured,
information may be transmitted to those services.

Installation and configuration currently require technical knowledge.

## Documentation

- [Installation](docs/getting-started/installation.md)
- [Configuration](docs/getting-started/configuration.md)
- [Operations](docs/operations.md)
- [Architecture](docs/development/architecture.md)
- [Develop a module](docs/development/module-development.md)
- [Local BootUI developer console](docs/development/bootui.md)
- [Build and verify OCI images](docs/development/oci-images.md)
- [Released module acceptance tests](docs/development/released-module-acceptance.md)

## Contributing

See the [contribution guide](docs/contributing.md) and
[contributor guidance](AGENTS.md).

## License

Zalava is licensed under the [Apache License 2.0](LICENSE).
