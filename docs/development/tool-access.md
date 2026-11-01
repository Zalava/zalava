# Member tool access

Administrators manage app pages, Member tool access, and Chat confirmation together from Settings → Manage accounts → Permissions. Choose a role default or an individual person; existing direct settings URLs remain available. Tool access defaults define Administrators only or All Members per
module/provider/tool. Each Member can inherit the role default or have an explicit Allow
or Deny override. Resetting an override restores inheritance. Existing overrides are
preserved on upgrade; tools with no configured default or override remain administrators-only.
Existing administrator access is unchanged. Start a module to discover its available tools.
Saved grants for stopped providers remain visible and can be revoked.

Grants and revocations persist in the host database, with the administrator and decision
time recorded in an append-only audit table. Execution reloads account state and the
current grant on every call, including execution of previously approved side effects.
Revocation prevents future invocations; it does not undo completed work or cancel work
already executing. Disabling an account also denies Member provider calls.

A grant cannot bypass mandatory scope, trust or administrator-only capability restrictions.
Side-effecting operations use the existing execution approval by default. Read-only provider tools run without confirmation by default. Administrators
can choose Chat confirmation for either kind of provider tool per action and role under Settings → Manage accounts → Permissions, with individual overrides taking precedence. “Existing
policy” retains prior actor/tool approval policies. Explicit “Require confirmation”
overrides an older always-allow policy; “Run without confirmation” applies only to
direct Chat actions and does not grant access or change other channels. Pending
requests still need a decision. When a read requires confirmation, approving it permits one retry of the exact read in the same conversation within five minutes; Chat asks the person to repeat the question so the model receives the result. The approval is bound to the same actor, role, provider, module, scope and arguments and cannot be used twice. Changes are audited and apply to subsequent calls. The same screen also configures direct Chat confirmation for classified built-in Knowledge, skill, provider-discovery, memory, clarification and module-development callbacks. Their existing access and mandatory safeguards still apply. Built-in callback approvals permit a single retry of the same request in the same conversation within five minutes. Requiring Knowledge or skill confirmation suppresses the respective automatic context injection; skill descriptions are also withheld from model tool metadata until a permitted read. Provider-dispatch confirmation is separate from the selected provider tool's own checks. Dynamically supplied or unclassified callbacks retain their existing rules.
Provider-side
argument validation and module-owned root/scope enforcement remain in force. The legacy
member-safe marker alone does not grant access in the configured host.

Each new Chat answer has always-visible elapsed timing and collapsed Execution details.
The assistant-ui message runtime carries host-measured timing and typed tool-call parts;
expanded details show context character metrics and bounded input/result/error previews.
Sensitive argument keys and configured secrets are redacted. Host-generated run IDs bind
answers and tool calls; identical answers remain distinct after reload. Older messages
without a stored run ID show unavailable details rather than guessed associations. Discovery availability and assistant prose are not proof of execution.
The current invocation log is bounded and process-local; installation history and Member
grants are persisted separately.

Module installation history is in Monitoring with request/decision date and time.
Pending installation decisions remain on Modules. Installed module rows combine saved
installation and runtime loading state. The Uninstall label retains the prior removal
operation and leaves saved configuration intact; a restart unloads existing classes.

Restart uses a self-contained progress response, tolerates temporary disconnects and
redirects to sign-in only after a different host generation is accepting traffic.
Failures and prolonged unavailability offer recovery without falsely reporting readiness.

Native provider provenance is established by the host catalog from actual loaded module
ownership, rather than requiring modules to self-declare the host's `zalava_backed` marker.
Existing unsafe/trust tags and missing provider scopes still prevent Member grants.
The optional Workspace drawer retains permissions, Memory proposals and background work;
run/tool transcripts belong to individual answers. Settings sections remain present across
account and tool-access screens.


## Module app access

Module app screens are controlled separately from provider tools and Chat confirmation. Administrators can open **Manage accounts → Permissions** to set the Member default for each registered page and an individual Member override. Members are denied by default; administrators retain access. Allowed pages appear in Apps, and the same policy applies to direct page requests and form submissions. Granting access to a page permits its registered page actions and exposes the data that page provides; it does not imply per-person data isolation. Existing CSRF protection applies. Changes are persisted and audited, and saved permissions remain visible for stopped apps so they can be revoked.
