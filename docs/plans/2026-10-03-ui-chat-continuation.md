# UI-CHAT-01 explicit channel continuation and workspace

Repository ownership: product in Zalava/zalava, public branch `step-ui-chat-01`
stacked on architecture acceptance #19; private dated plan/evidence on
`step-ui-chat-evidence-01`, stacked on #451. User authorized continuation through
ZALAVA-LOCAL-01 and real dependent stacks before predecessor merges. Never merge.

## Bounded design

Persist actor-owned conversation origin beside its existing YAML messages, and
retain it when messages change. Channel ingress resolves/creates its actor-owned
conversation through a conversation inbound port, rather than generating an
unpersisted reference that the chat use case rejects. The channel adapter supplies
only the already authenticated actor and origin. Stable external identity is
checked again for continuation. Web sends require a web origin; channel histories
are readable but cannot be silently taken over by a web composer.

First supported continuation destination is explicitly selected private web chat.
Create a separate actor-owned conversation with public user/assistant history,
excluding system messages; retain source history and existing jobs/approvals.
Shared destinations, unsupported destinations, foreign owners, disabled accounts
and revoked/conflicting external links fail closed. Do not add outbound channel
handoff without destination-specific delivery and confirmation evidence.

Frontend uses the existing assistant-ui external store/runtime and WebSocket
protocol. Add a responsive contextual inspector of authoritative recent job
states, approval summaries and available knowledge attachments; do not claim
these are hidden model context/reasoning. Disable sending for read-only channel
histories, expose explicit private-web continuation, and keep per-conversation
stream events isolated. Retain navigation/keyboard/focus feedback conventions.

## Maintained library check

Existing Spring Security actor resolver, YAML parser/serializer, conversation
store and channel identity-link port cover authority/persistence. No new JVM
library or SDK extension is needed. Context7 resolved `/assistant-ui/assistant-ui`
and verified `useExternalStoreRuntime` send gating and external thread selection.

## Acceptance

Unit/persistence regressions: origin survives writes and store recreation;
channel routing reuses the stored reference; copied histories remain separate;
foreign/disabled/revoked/shared/unsupported cases denied.
Full-context HTTP/WebSocket regression: selecting non-web history remains
read-only; implicit send denied; explicit private-web continuation succeeds;
revocation and cross-account operations denied.
Real browser journeys: inspect channel fixture history, choose private web
continuation, send a subsequent message, retain original history, reload and
observe persisted separation; exercise empty/error inspector and compact/medium/
expanded layouts with keyboard/focus and no horizontal overflow. Use local
channel/model fixtures; do not claim a live Telegram-provider journey.
Required final gate: `GRADLE_USER_HOME=/tmp/gradle-home ./gradlew :module-api:test :module-api-test:check :app:check`.

## Implementation and focused evidence

- Channel origin is persisted in actor-private YAML frontmatter and retained across message writes. Ingress resolves an existing owned conversation or persists a new one before chat dispatch. Storage/dispatch failures remove the deduplication key so retries remain possible.
- Explicit `chat.continue` rechecks the enabled actor, ownership, private source, and current identity-link `chat:send` grant. The only supported destination is private web chat. A distinct reference copies USER/ASSISTANT history; SYSTEM messages and existing job/approval ownership remain at the source.
- JSON and legacy web adapters reject implicit sends to channel histories. Snapshot flags reflect current authority; the composer is available only for web origins.
- Chat has Run/Tools/Context details sourced from account-owned state, a pending-permission shortcut, server-acknowledged decisions, responsive disclosure, and conversation-scoped stream filtering.
- Existing assistant-ui/Spring/YAML/identity ports were reused; no dependency was introduced.
- Focused domain/channel tests passed; the real-browser continuation journey passed. Full-context websocket acceptance verifies foreign-owner/revoked-link denial and implicit-send rejection. Browser acceptance uses local fixtures and isolated PostgreSQL/workspace data; it does not claim a live Telegram journey.
- The initial full host gate passed ordinary tests, architecture, coverage, and all twelve released-module acceptance cases; one browser journey required updating for the new inspector. Final gate pending after that correction.
- Diagnostics: `/tmp/zalava-ui-chat-focused.log`, `/tmp/zalava-ui-chat-browser.log`, `/tmp/zalava-ui-chat-full-gate.log`, `app/build/browser-acceptance/chat-tool-trace.zip`.

## Material development corrections

Full-context acceptance exposed multiple bean aliases for the persisted conversation store; the new composition bean now explicitly qualifies both actor and store ports. Existing browser acceptance exposed duplicate failure announcements and the changed approval-control location; feedback was deduplicated and approval acceptance follows the explicit inspector route. Empty Telegram settings activate the legacy starter; the new fixture uses the established `false` settings to prevent external polling.

The complete browser lane also exposed a CDN-dependent sidebar width (241px instead of the reviewed 240px when Bulma's reset is absent). The host now owns its sidebar `box-sizing`, and browser acceptance checks the same width after explicitly blocking the external stylesheet. No assertion tolerance or coverage floor was relaxed.
