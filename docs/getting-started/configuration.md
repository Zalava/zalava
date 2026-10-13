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

## Restart after installing modules

An administrator can use **Modules → Restart Zalava to load installed modules**.
When the local service-manager dispatcher is installed, Zalava requests its
managed restart. Otherwise, Spring Cloud Context's Actuator `RestartEndpoint`
restarts the application context inside the same JVM. The web connection closes
briefly; sign in again after startup. Provider settings apply immediately and
do not require this restart.

The application restart journal lives beside the workspace in
`<workspace-name>-application-control/zalava-runtime-restart`; the host must be
able to write that directory. It is separate from the dispatcher signal inside
the workspace, so one request cannot trigger both restart mechanisms. Keep this
control directory host-owned and preserve it when upgrading. Failed startup
may require your service manager to recover the application; consult host logs.

The UI invokes the maintained endpoint internally through an administrator-only,
CSRF-protected action. HTTP exposure of `/actuator/restart` is unnecessary and
is excluded by default. If explicitly exposed, writes require administrator
authority and CSRF protection. The separate management listener permits
anonymous access only to health, info, Prometheus and metrics endpoints;
other endpoints require an administrator. Contexts without a captured starting
Spring application report restart as unavailable rather than accepting a no-op.
