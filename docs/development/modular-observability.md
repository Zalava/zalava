# Modular observability architecture (proposal)

Status: design proposal; implementation requires code-level verification.

## Goals
Provide useful local-first dashboards for chat latency (end-to-end and time-to-first-token), LLM latency and usage, context composition/window pressure, agent iterations, tools, module-provided services, memory, JVM, PostgreSQL and HTTP. Support drill-down from a slow chat to traces, logs and persisted run records. No monitoring stack is required for normal operation.

## Existing boundaries and verification
Architecture documentation assigns host-wide metrics to `platform.observability`, module runtime/managed services/web extensions to `modules`, and UI/control to `web`. Configuration documentation describes a separate management listener with metrics and Prometheus endpoints. Preserve these boundaries and existing authentication. Before implementation, inspect actual Java source, Spring AI/Micrometer dependencies, managed OCI service contracts, module page registration, lifecycle and permissions; documentation alone does not establish which APIs are implemented.

## Design
1. **Core instrumentation:** Zalava host owns the observation points and low-cardinality metric vocabulary. Prefer existing Spring Boot Actuator, Micrometer Observation, Spring AI observations and OpenTelemetry GenAI semantic conventions. Capture chat request/streaming first token/final completion, provider and model calls, tool invocation and permission wait, module service calls, memory retrieval, agent iterations, retries, queueing and failures. Use run and trace IDs only in spans/logs, never metric labels.
2. **Module API:** add only the smallest stable telemetry contract needed for optional module-specific observations and trace propagation. Existing host observations must not depend on an observability module being installed. Do not expose host Spring application objects to modules. Verify whether the existing managed-service and module-web APIs suffice before expanding the API.
3. **OTLP exporter module:** independently released module, configures collector endpoint, resource attributes, sampling and export. Check whether host bootstrap instrumentation needs an always-present adapter or host configuration rather than late module loading. No double instrumentation or duplicate export.
4. **OCI observability service module:** use existing approved OCI service requests to provision/start/stop and report health for an OpenTelemetry collector and Grafana LGTM stack. Verify OCI support for image, volumes, network, credentials, ports, resource limits and teardown. Use `grafana/otel-lgtm` for local development only; provide separate persistent production-oriented services as a later option. Never start privileged containers or expose admin ports without explicit admin approval.
5. **Grafana UI module:** use existing module page registration to expose an admin-only Observability page. Prefer an authenticated link/open-in-Grafana with contextual trace/run links first. Embed only if CSP, authentication, frame policies and cross-origin isolation are solved safely; never place Grafana admin tokens in the browser. Provide clear not-installed, provisioning, starting, healthy, unavailable and error states.
6. **Dashboard assets:** provision versioned Grafana dashboards/data sources declaratively from the module, not by manual clicking. Ship Overview, LLM/Context, Tools/Modules, Agent Traces and JVM/Infrastructure dashboards; filters for model, module, environment and time. Add alerts after validating baselines.
7. **Run history:** link traces to persisted agent run records via IDs, without replacing durable execution history with sampled telemetry.

## Proposed telemetry
| Area | Signals |
| --- | --- |
| Chat | request-to-first-token, full answer latency, throughput, error/abort |
| LLM | provider/model, request latency, tokens in/out, cached tokens when available, retries, estimated cost |
| Context | system/history/memory/tool schema/tool results estimated tokens; provider-reported input usage; utilization against configured model window |
| Agent | run duration, iteration count, active/queued runs, retries |
| Tools/services | calls, p50/p95/p99 latency, timeouts, errors, permission wait, module/service IDs |
| Memory | retrieval latency, results, enrichment time |
| Runtime | CPU, memory, GC, threads, HTTP, PostgreSQL connection pool, OCI service health |

Avoid claiming precise token breakdown when only an estimate is possible. Maintain configurable model prices and distinguish estimated from billed cost.

## Privacy, resilience and operations
Default to metadata only: no prompts, responses, tool arguments, secrets or user identifiers in telemetry. Sanitize exception attributes. Explicit opt-in is required for sensitive content capture. Metrics use bounded labels; tracing sampling, retention, batching and exporter backpressure are configurable. Export must be non-blocking and fail-open. Preserve operation when modules are absent or their OCI services are down. Provide explicit volume persistence, backup, upgrades, memory/CPU limits, port binding and clean uninstall behavior. A dashboard must never expose one user's data to another without authorization.

## Delivery slices
1. **Discovery:** audit current source code, dependencies, instrumentation, module service OCI and page APIs; document gaps and alternatives.
2. **Host metrics:** add missing measurements and deterministic tests, verify no exporter required.
3. **OCI stack module:** managed services lifecycle and safe local Docker Compose fallback.
4. **Grafana UI module:** admin page, provisioning and dashboards; verify mobile/tablet/desktop, keyboard and loading/error states.
5. **Tracing module:** end-to-end spans and run-to-trace navigation; optional alerts and retention controls.

## Acceptance
- Normal Zalava operation without any observability module.
- Install/enable observability with explicit approval, persistent storage and no host source changes.
- One test chat trace links model and tool spans to its run record.
- Dashboards show token usage, chat percentiles, tool timings, JVM/DB and failures.
- No prompt text or secrets in default telemetry; no unbounded metric labels.
- Failed/removed OCI services do not block agent execution.
- Integration and module-contract tests, permission tests and browser visual verification follow AGENTS.md.
- Private execution plan, screenshots and acceptance evidence remain in `cordin/zalava-dev`; no private plans in public repository.


## Source-level feasibility review (2026-10-15)
Verified against current `main` Java sources:
- `module-api/.../extensions/managed/ManagedServiceDeclaration.java` and `ManagedServiceDesiredState.java`: modules can declare managed OCI services, digest-pinned images, dependencies, resource limits, ports, paths, secrets and lifecycle. `DefaultDeclaredManagedServices` discovers module declarations. `ModuleManagedServiceEngine` delegates to an optional module-provided engine through `RuntimeQueries.findService`. **Feasible** to declare an observability service stack, subject to the existing engine being installed and admin grants.
- `module-api/.../extensions/web/ZalavaWebExtension.java`, `WebExtensionRegistry.java`, `WebPageRegistration.java`: modules register GET/POST pages. `DefaultWebExtensionRoutes` discovers and routes them; `ZalavaWebExtensionController` exposes `/apps/{moduleId}/{pageId}` using the existing host view. **Feasible** to create an Observability page without a new UI framework.
- `app/.../platform/observability/adapter/out/micrometer/MicrometerOperationalMetrics.java`: existing metrics cover agent runs, context characters by source, tool invocation counts/timings, task execution and knowledge lifecycle; label cardinality is bounded and errors are fail-open. Reuse this adapter, expand via ports and observations rather than replacing it.
- `app/build.gradle`: Spring AI model starters, Actuator and Prometheus registry are present. No explicit Micrometer tracing bridge or OTLP exporter dependency found. Traces and export are therefore an implementation gap; check transitive dependency and configuration behavior before choosing a bootstrap approach.

### Confirmed constraints and follow-up design decisions
1. OCI desired state requires an image digest and administrator-approved limits/paths/ports/secrets. Multiple service declarations and dependency ordering are supported at the API level, but collector-to-Grafana networking, provisioned configuration files and service-to-service DNS require an end-to-end integration test.
2. Module web pages return host-wrapped HTML; the current page API does not directly grant an authenticated Grafana reverse proxy or secure iframe. Prefer an admin-only module status/configuration page plus an external Grafana link. Embedding requires a separate security/CSP/auth design.
3. Do **not** make a late-loaded module solely responsible for attaching Spring instrumentation to already-created beans. Keep host-side observations/export bootstrap in the host; make collector endpoint, stack management and dashboard provisioning module-driven. Evaluate whether an exporter module can configure the host through a narrow supported contract; otherwise provide a host-configured OTLP exporter and keep infrastructure/UI modular.
4. Module web handler authorization and HTML sanitization need dedicated security tests, especially for admin-only observability and multi-user isolation.
5. Confirm OCI engine image support, persistent storage, port mapping and network topology through integration tests; API declarations alone are not proof of working LGTM deployment.

### Proposed first implementation PRs
A. Host tracing foundation and missing chat/LLM/context metrics (small, independently testable).
B. OCI observability stack module using existing managed-service declarations, admin grants and persistent volumes.
C. Grafana UI module with admin-only status page, provisioned dashboards and safe navigation.
D. End-to-end trace/run correlation and dashboard acceptance tests.

This review is static source inspection; no Gradle build, packaged host, OCI engine or browser tests have been executed.
