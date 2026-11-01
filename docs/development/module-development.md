# Develop a Zalava module

Develop modules in their own repository using the public template and a released Zalava Module API version. Do not bundle the API in a module JAR. Declare stable module, factory, and provider identifiers, configuration requirements, runtime compatibility, and least permissions. Missing first-use configuration must be recoverable rather than fail host startup.

Test the built artifact with the released module contract-test kit, including provider creation, configuration outcomes, operations, cleanup, metadata, permissions, and compatibility. Release an immutable JAR with a SHA-256 checksum and release-index evidence. Publishing does not install the module; users review and approve installation in the host.

## Configuration form guidance

Module configuration uses JSON Schema. Supply `title` and `description` on fields for readable labels and help text. Give a nested object a `title` and optional `description` to present its fields together under a named section. Untitled fields remain under Other settings. Grouping is presentation only: configuration paths, validation, secrets and factory scopes do not change. The nearest titled parent supplies a field’s section. Keep the catalog and packaged schemas identical.
