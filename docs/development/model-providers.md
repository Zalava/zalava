# Model provider settings

Administrators configure the chat provider in **Settings → Provider & workspace**.
Chat links to that section when no model is configured. Choose a provider to show
its model, endpoint and credential fields. Existing credentials are never shown;
leave a credential blank to retain it, or explicitly remove/replace it.

The packaged Spring AI 2.0.0 integrations cover OpenAI, Anthropic, Ollama, Google
Gemini/Vertex AI, Mistral AI, DeepSeek and Amazon Bedrock Converse. Microsoft
Foundry/Azure OpenAI and GitHub Models use the OpenAI integration. Named compatible
endpoints include Groq, NVIDIA, Perplexity and MiniMax; another OpenAI-compatible
endpoint can be supplied, including local endpoints without authentication.
Models and features depend on the selected service and account.

Saving prepares a native Spring AI client and applies it immediately to new
requests after the settings are persisted. Current requests finish using their
original client and options. No restart is needed for provider or model changes.
Saving does not call a paid service or verify connectivity; try Chat afterward.
Failed client initialization or persistence preserves the previous provider.
Cloud providers require reachable services and valid credentials; Vertex
credential files and AWS profiles must be available inside the actual host/container.

Provider state is stored atomically in `MODEL-PROVIDER.private.yaml` under a
private directory beside the workspace: a workspace `/data/workspace` uses
`/data/workspace-model-config/`. The directory is mode 0700 and file is 0600.
This keeps credentials outside model-visible workspace content and the managed
installation's read-only configuration mount. Treat the file as private data;
exclude it from source control and public diagnostics.

On restart, the registered Spring Boot environment processor loads only the
selected provider's catalog-defined settings before Spring AI auto-configuration.
Saved provider settings take precedence over launcher defaults such as
`spring.ai.model.chat=unknown`. Other application properties are unaffected.
If the private file is corrupt, stop the host and restore a known-good private
copy or move that file aside before restarting; do not paste its contents into
logs or issue reports. Provider-specific service errors must be resolved using
the provider's credentials, model access and endpoint configuration.

Old onboarding GET links redirect to this Settings section. The six-stage
wizard and its mutating endpoints are retired. Workspace instructions and module
configuration use their existing Settings and Modules destinations.

## Client lifecycle

The model configuration context owns prepare/persist/activate ports and an
internal runtime state query. Its Spring adapter uses the packaged provider
auto-configurations in a separate non-web context. The agent adapter acquires a
client generation for the complete operation, including streamed tool rounds.
Each generation pairs its client with its tool-support flag; options from a
previous provider do not carry over. Retired contexts close after their final
operation completes. The initial startup client remains owned by the host.

Generation changes and shutdown are serialized. Bedrock SDK pools are explicit
closeable beans in the candidate context; Spring AI accepts these clients without
creating hidden pools. Other provider lifecycle callbacks remain framework-owned.
This preserves the existing per-request tool policy and owner-scoped chat memory.

Using native auto-configuration keeps provider-specific construction in Spring AI.
Mutating startup properties alone would leave existing clients and cached options
unchanged. Hand-building every provider would duplicate SDK configuration and
lifecycle behavior. Each supported choice is checked through the production
factory, with real local HTTP and packaged-browser journeys for observable switching.
