# Member tool access

Administrators manage individual Member tool grants from Settings → Manage accounts →
Manage tool access. Each grant identifies an account, module, provider and tool; it does
not grant other tools from the same provider. Tools without a grant are denied to Members.
Existing administrator access is unchanged. Start a module to discover its available tools.
Saved grants for stopped providers remain visible and can be revoked.

Grants and revocations persist in the host database, with the administrator and decision
time recorded in an append-only audit table. Execution reloads account state and the
current grant on every call, including execution of previously approved side effects.
Revocation prevents future invocations; it does not undo completed work or cancel work
already executing. Disabling an account also denies Member provider calls.

A grant cannot bypass mandatory scope, trust or administrator-only capability restrictions.
Side-effecting operations still require the existing execution approval. Provider-side
argument validation and module-owned root/scope enforcement remain in force. The legacy
member-safe marker alone does not grant access in the configured host.

Conversation-owned tool evidence is visible in Chat's Tools panel and relevant Run details.
It contains bounded input/result previews; sensitive argument keys and configured secrets
are redacted. Discovery availability and assistant prose are not proof of execution.
The current invocation log is bounded and process-local; installation history and Member
grants are persisted separately.

Module installation history is in Monitoring with request/decision date and time.
Pending installation decisions remain on Modules. Installed module rows combine saved
installation and runtime loading state. The Uninstall label retains the prior removal
operation and leaves saved configuration intact; a restart unloads existing classes.

Restart uses a self-contained progress response, tolerates temporary disconnects and
redirects to sign-in only after a different host generation is accepting traffic.
Failures and prolonged unavailability offer recovery without falsely reporting readiness.
