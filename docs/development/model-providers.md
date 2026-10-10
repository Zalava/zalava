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

Saving records configuration and shows **Restart required**. It does not validate
credentials or call a paid service. Restart Zalava through the existing authorized
runtime controls, then try Chat. The running provider remains separately visible
while saved changes await restart. Cloud providers require reachable services and
valid credentials; Vertex credential files and AWS profiles must be available
inside the actual host/container.

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
