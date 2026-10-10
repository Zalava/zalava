package org.zalava.assistant.models.configuration.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Provider fields follow the BOM-pinned Spring AI 2.0 configuration contract. */
public final class ChatProviderCatalog {
  private ChatProviderCatalog() {}

  public record Field(
      String name,
      String label,
      String property,
      boolean secret,
      boolean required,
      String defaultValue) {}

  public record Provider(
      String id,
      String label,
      String runtimeId,
      List<Field> fields,
      Map<String, Object> fixedProperties,
      String help) {
    public Provider {
      fields = List.copyOf(fields);
      fixedProperties = Map.copyOf(fixedProperties);
    }
  }

  private static Field field(
      String name, String label, String property, boolean secret, boolean required, String value) {
    return new Field(name, label, property, secret, required, value);
  }

  private static List<Field> apiFields(String prefix, String baseUrl) {
    var fields = new ArrayList<Field>();
    fields.add(field("model", "Model", prefix + ".chat.model", false, true, ""));
    fields.add(field("apiKey", "API key", prefix + ".api-key", true, true, ""));
    if (baseUrl != null)
      fields.add(field("baseUrl", "Endpoint URL", prefix + ".base-url", false, true, baseUrl));
    return fields;
  }

  private static Provider api(String id, String label, String runtime, String prefix, String url) {
    return new Provider(
        id,
        label,
        runtime,
        apiFields(prefix, url),
        Map.of(),
        "Enter a model identifier available to your account. Saving does not verify connectivity or credentials.");
  }

  private static Provider openAi(String id, String label, String url, Map<String, Object> flags) {
    return new Provider(
        id,
        label,
        "openai",
        apiFields("spring.ai.openai", url).stream()
            .map(
                f ->
                    id.equals("openai-compatible") && f.name().equals("apiKey")
                        ? new Field(f.name(), "API key (optional)", f.property(), true, false, "")
                        : f)
            .toList(),
        flags,
        id.equals("perplexity")
            ? "Chat answers only: Perplexity does not support tool execution. Enter a model available to your account."
            : "Uses the provider's OpenAI-compatible endpoint. Enter a model available to your account.");
  }

  public static List<Provider> providers() {
    return List.of(
        new Provider(
            "ollama",
            "Ollama",
            "ollama",
            List.of(
                field("model", "Model", "spring.ai.ollama.chat.model", false, true, ""),
                field(
                    "baseUrl",
                    "Endpoint URL",
                    "spring.ai.ollama.base-url",
                    false,
                    true,
                    "http://localhost:11434")),
            Map.of(),
            "No API key is required. The endpoint must be reachable from the Zalava host/container."),
        api("openai", "OpenAI", "openai", "spring.ai.openai", "https://api.openai.com/v1"),
        api(
            "anthropic",
            "Anthropic",
            "anthropic",
            "spring.ai.anthropic",
            "https://api.anthropic.com"),
        api("google-genai", "Google Gemini", "google-genai", "spring.ai.google.genai", null),
        new Provider(
            "google-vertex",
            "Google Vertex AI",
            "google-genai",
            List.of(
                field("model", "Model", "spring.ai.google.genai.chat.model", false, true, ""),
                field(
                    "projectId",
                    "Cloud project",
                    "spring.ai.google.genai.project-id",
                    false,
                    true,
                    ""),
                field("location", "Location", "spring.ai.google.genai.location", false, true, ""),
                field(
                    "credentialsUri",
                    "Credentials file URI (optional)",
                    "spring.ai.google.genai.credentials-uri",
                    false,
                    false,
                    "")),
            Map.of("spring.ai.google.genai.vertex-ai", true, "spring.ai.google.genai.api-key", ""),
            "Uses application-default Google Cloud credentials, or a file: URI accessible inside the Zalava host/container."),
        api("mistral", "Mistral AI", "mistral", "spring.ai.mistralai", "https://api.mistral.ai"),
        api("deepseek", "DeepSeek", "deepseek", "spring.ai.deepseek", "https://api.deepseek.com"),
        new Provider(
            "bedrock-converse",
            "Amazon Bedrock Converse",
            "bedrock-converse",
            List.of(
                field(
                    "model",
                    "Model or inference profile",
                    "spring.ai.bedrock.converse.chat.model",
                    false,
                    true,
                    ""),
                field(
                    "region",
                    "AWS region",
                    "spring.ai.bedrock.aws.region",
                    false,
                    true,
                    "us-east-1"),
                field(
                    "accessKey",
                    "AWS access key (optional)",
                    "spring.ai.bedrock.aws.access-key",
                    true,
                    false,
                    ""),
                field(
                    "secretKey",
                    "AWS secret key (optional)",
                    "spring.ai.bedrock.aws.secret-key",
                    true,
                    false,
                    ""),
                field(
                    "sessionToken",
                    "AWS session token (optional)",
                    "spring.ai.bedrock.aws.session-token",
                    true,
                    false,
                    ""),
                field(
                    "profileName",
                    "AWS profile (optional)",
                    "spring.ai.bedrock.aws.profile.name",
                    false,
                    false,
                    "")),
            Map.of(),
            "Leave access/secret keys empty to use the host's AWS credential chain. Supply both keys when using explicit credentials."),
        new Provider(
            "microsoft-foundry",
            "Microsoft Foundry / Azure OpenAI",
            "openai",
            List.of(
                field("model", "Model", "spring.ai.openai.chat.model", false, true, ""),
                field("apiKey", "API key", "spring.ai.openai.api-key", true, true, ""),
                field("baseUrl", "Endpoint URL", "spring.ai.openai.base-url", false, true, ""),
                field(
                    "deploymentName",
                    "Deployment name",
                    "spring.ai.openai.microsoft-deployment-name",
                    false,
                    true,
                    "")),
            Map.of("spring.ai.openai.microsoft-foundry", true),
            "Use your Microsoft Foundry endpoint and deployed model."),
        openAi(
            "github-models",
            "GitHub Models",
            "https://models.github.ai/inference",
            Map.of("spring.ai.openai.git-hub-models", true)),
        openAi("groq", "Groq", "https://api.groq.com/openai/v1", Map.of()),
        openAi("nvidia", "NVIDIA", "https://integrate.api.nvidia.com/v1", Map.of()),
        openAi(
            "perplexity",
            "Perplexity",
            "https://api.perplexity.ai",
            Map.of("zalava.model.tool-calling-enabled", false)),
        api(
            "minimax",
            "MiniMax",
            "anthropic",
            "spring.ai.anthropic",
            "https://api.minimax.io/anthropic"),
        openAi("openai-compatible", "Other OpenAI-compatible provider", "", Map.of()));
  }

  public static Provider require(String id) {
    return providers().stream()
        .filter(p -> p.id().equals(id))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Choose a supported provider."));
  }

  public static boolean supportsRuntime(String id) {
    return providers().stream().anyMatch(p -> p.runtimeId().equals(id));
  }
}
