# Zalava architecture

## Source ownership

The host has eight source ownership groups beneath `org.zalava`: `identity`,
`assistant`, `capabilities`, `tasks`, `knowledge`, `modules`, `platform`, and
`web`. These groups organize related feature contexts; they do not create new
Gradle projects or merge independent use cases. Each substantial child context
keeps its domain, application ports and adapters.

`modules` owns catalog/installation, module runtime, managed services, development
and module web extensions. `platform.observability` owns shared operational
metrics across agent, task, knowledge and provider execution. Module runtime
does not own all host observability. Host UI/control live in `web`.
External modules may still use their own `org.zalava.modules.<module>` namespace;
the loader protects specific host module-management subpackages instead of
reserving that entire prefix.

Spring composition may wire concrete adapters. Feature/use-case collaboration
must use explicit contracts instead of another context's internal implementations.

## Context boundaries

Organize product code around bounded contexts rather than technical layers.
Each context owns its domain model, use cases, and persistence-facing contracts.
Cross-context collaboration uses a deliberately stable contract; contexts do
not import one another's internal adapters or persistence models.

Within a context, inbound adapters translate HTTP, jobs, commands, or other
transport into use-case inputs. Use cases apply domain behavior and use explicit
outbound ports. Outbound adapters implement those ports and contain framework,
database, messaging, and SDK mapping. Domain and use-case code stays independent
of those technologies. The composition root supplies concrete adapters.

A bounded context is a source-level ownership boundary, not automatically a
Gradle subproject. Introduce a separate build module only for a demonstrated
need such as independent release, compilation, ownership, or dependency
isolation.

The public repository is a Gradle multi-project build: `module-api` is the stable external-module contract, `module-api-test` is the module contract-test kit, and `app` is the Spring Boot host and adapters.

The host owns policy enforcement, validation, persistence, lifecycle, authorization, and audit boundaries. Business capabilities use explicit ports; adapters connect frameworks and external systems. Modules supply provider factories and instances, allowing configuration, permission, lifecycle, and audit decisions to attach to the provider that performs work. Modules do not depend on each other directly or receive arbitrary host application objects.

Browser CI must use deterministic fixtures without local provider credentials.
Provider acceptance uses the integrated Settings form and verifies persistence
across a restart of the packaged host. See [model provider configuration](model-providers.md)
for the supported integrations and configuration lifecycle. Navigation acceptance verifies the reviewed
sidebar, card layout and surface color and retains its screenshot; a PNG byte
hash is not a portable pixel comparison across operating-system font renderers.
Generated template build output must not enter the host distributable.

## Framework boundaries

Account and knowledge lifecycle use cases remain framework independent. Spring transaction decorators live in outbound infrastructure adapters and are wired only in composition. Administrator count-and-mutation operations retain SERIALIZABLE isolation; every existing knowledge lifecycle transaction, including read-only ownership lookup, is preserved. Password hashing uses the AccountPasswords port. Knowledge ownership denial is a domain exception; the existing UI retains its failure handling.

The newer module-channel host runtime also lives in assistant.channels.runtime; it does not introduce a ninth ownership group. Failed ingress dispatch can be retried, and closing the runtime removes every registered transport.

Agent tool selection uses the generic RegisteredToolCallbacks contract. Runtime generation management uses ModuleLifecycleStore and ModuleConfigurations rather than filesystem implementations. The UI reads bootstrap verification through BootstrapVerificationQueries. Provider argument decoding uses ToolArgumentDecoder; Jackson remains in its adapter with the same precision, null and validation behavior. Development artifact execution and schema/assertion evaluation are contract adapters behind CandidateEvaluator.

Architecture checks enforce framework independence in these migrated application contexts and use negative transaction/Jackson fixtures. Existing component and browser journeys cover account authority, knowledge isolation, module generations, control queries and permissioned tool execution. Transaction metadata checks guard isolation and rollback policy.
