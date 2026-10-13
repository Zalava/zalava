# Configure Zalava

Keep secrets and mutable user data outside source control and a replaceable JAR
or image. Supply provider credentials, database settings, and module credentials
through the host's private configuration mechanism. Never commit API keys,
tokens, database passwords, user prompts, or personal workspace data.

Modules receive only their declared scoped configuration and opaque secret
references; they must not read arbitrary host configuration files. Use
PostgreSQL for persistent data and retain data, private configuration, and
approved module artifacts when upgrading.


## Missing capabilities and module suggestions

When installed provider tools cannot fulfill a request, Zalava's recommendation
tool first searches stable module catalog descriptions. Suggestions are possible
metadata matches: the administrator reviews the module, release, compatibility
and permissions in **Modules** before explicitly installing and loading it.
Members receive an administrator handoff. Search never installs a module.

Suggestions use the configured `zalava.catalog.module-locator.url` and catalog
access credentials. They fetch catalog metadata only when the recommendation
tool is called; the private search query is matched locally and is not sent to
an external search service. Set `zalava.catalog.suggestions.enabled=false` to
disable these lookups. Disabled or failed catalog access does not establish that
no matching module exists. Only a successful no-match permits suggesting a
separate, explicitly confirmed module development request.
