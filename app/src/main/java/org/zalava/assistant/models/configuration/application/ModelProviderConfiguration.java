package org.zalava.assistant.models.configuration.application;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.zalava.assistant.models.configuration.application.port.out.ModelProviderActivation;
import org.zalava.assistant.models.configuration.domain.ChatProviderCatalog;
import org.zalava.assistant.models.configuration.domain.ChatProviderCatalog.Provider;
import org.zalava.platform.configuration.application.port.out.ConfigurationStore;

/** Validates and persists selection, with prepared live activation when the host supports it. */
public final class ModelProviderConfiguration {
  private final ConfigurationStore store;
  private final ModelProviderActivation activation;
  private final BiFunction<String, String, String> environment;

  public ModelProviderConfiguration(
      ConfigurationStore store, BiFunction<String, String, String> environment) {
    this(store, environment, ModelProviderActivation.onRestart());
  }

  public ModelProviderConfiguration(
      ConfigurationStore store,
      BiFunction<String, String, String> environment,
      ModelProviderActivation activation) {
    this.store = store;
    this.environment = environment;
    this.activation = activation;
  }

  public record DisplayField(
      String name,
      String label,
      boolean secret,
      boolean required,
      String value,
      boolean savedSecret) {}

  public record Display(
      Provider provider, List<DisplayField> fields, String runningLabel, boolean restartRequired) {}

  public synchronized Display display(String requested) throws IOException {
    Map<String, Object> saved = store.read();
    String activeId = selected(saved);
    String id = requested == null || requested.isBlank() ? activeId : requested;
    if (id.isBlank()) id = "openai";
    Provider provider = ChatProviderCatalog.require(id);
    Map<String, String> values = values(saved, provider);
    var fields =
        provider.fields().stream()
            .map(
                field -> {
                  String value = values.getOrDefault(field.name(), field.defaultValue());
                  return new DisplayField(
                      field.name(),
                      field.label(),
                      field.secret(),
                      field.required(),
                      field.secret() ? "" : value,
                      field.secret() && !value.isBlank());
                })
            .toList();
    String running = environment.apply("spring.ai.model.chat", "unknown");
    String runningLabel =
        ChatProviderCatalog.providers().stream()
            .filter(p -> p.id().equals(environment.apply("zalava.model.provider", running)))
            .map(Provider::label)
            .findFirst()
            .orElse("Not configured");
    boolean pending =
        !saved.isEmpty()
            && runtimeProperties(saved).entrySet().stream()
                .anyMatch(
                    entry ->
                        !String.valueOf(entry.getValue())
                            .equals(environment.apply(entry.getKey(), "")));
    return new Display(provider, fields, runningLabel, pending);
  }

  public synchronized void save(String id, Map<String, String> input) throws IOException {
    Provider provider = ChatProviderCatalog.require(id);
    Map<String, Object> saved = store.read();
    Map<String, String> previous = values(saved, provider);
    var values = new LinkedHashMap<String, String>();
    for (var field : provider.fields()) {
      String value = input.getOrDefault(field.name(), "").strip();
      if (field.secret() && value.isBlank() && !"true".equals(input.get("clear-" + field.name())))
        value = previous.getOrDefault(field.name(), "");
      if (field.required() && value.isBlank())
        throw new FieldValidationException(
            "Enter " + field.label().toLowerCase() + ".", field.name());
      if (value.length() > 8192 || value.contains("\n") || value.contains("\r"))
        throw new FieldValidationException(
            "Enter a valid " + field.label().toLowerCase() + ".", field.name());
      if (field.name().equals("baseUrl") && !value.isBlank()) {
        URI uri;
        try {
          uri = URI.create(value);
        } catch (IllegalArgumentException e) {
          throw new FieldValidationException("Enter a valid endpoint URL.", field.name());
        }
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
            || uri.getHost() == null
            || uri.getUserInfo() != null
            || uri.getFragment() != null)
          throw new FieldValidationException(
              "Enter an http or https endpoint URL without embedded credentials.", field.name());
      }
      if (field.name().equals("credentialsUri") && !value.isBlank() && !value.startsWith("file:"))
        throw new FieldValidationException(
            "Use a file: URI for credentials accessible to the Zalava host.", field.name());
      values.put(field.name(), value);
    }
    if (id.equals("bedrock-converse")
        && values.get("accessKey").isBlank() != values.get("secretKey").isBlank())
      throw new FieldValidationException(
          "Provide both AWS access and secret keys, or leave both empty.",
          "accessKey",
          "secretKey");
    var configurations = configurations(saved);
    configurations.put(id, values);
    Map<String, Object> candidate = Map.of("provider", id, "configurations", configurations);
    try (var prepared = activation.prepare(runtimeProperties(candidate))) {
      store.write(candidate);
      prepared.commit();
    }
  }

  public static final class FieldValidationException extends IllegalArgumentException {
    private final List<String> fields;

    public FieldValidationException(String message, String... fields) {
      super(message);
      this.fields = List.of(fields);
    }

    public List<String> fields() {
      return fields;
    }
  }

  private String selected(Map<String, Object> saved) {
    String id =
        String.valueOf(
            saved.getOrDefault(
                "provider",
                environment.apply(
                    "zalava.model.provider",
                    environment.apply("spring.ai.model.chat", "unknown"))));
    return ChatProviderCatalog.providers().stream().anyMatch(p -> p.id().equals(id)) ? id : "";
  }

  private Map<String, String> values(Map<String, Object> saved, Provider provider) {
    Object previous = configurations(saved).get(provider.id());
    var values = new LinkedHashMap<String, String>();
    for (var field : provider.fields()) {
      if (previous instanceof Map<?, ?> map && map.get(field.name()) instanceof String value)
        values.put(field.name(), value);
      else if (saved.isEmpty() && selected(saved).equals(provider.id())) {
        String value = environment.apply(field.property(), null);
        if (value == null && field.name().equals("model"))
          value =
              environment.apply(
                  field.property().replace(".chat.model", ".chat.options.model"), null);
        if (value != null) values.put(field.name(), value);
      }
    }
    return values;
  }

  private static Map<String, Object> configurations(Map<String, Object> saved) {
    var result = new LinkedHashMap<String, Object>();
    if (saved.get("configurations") instanceof Map<?, ?> map)
      map.forEach(
          (key, value) -> {
            if (key instanceof String text) result.put(text, value);
          });
    return result;
  }

  public static Map<String, Object> runtimeProperties(Map<String, Object> saved) {
    if (saved.isEmpty()) return Map.of();
    Provider provider = ChatProviderCatalog.require(String.valueOf(saved.get("provider")));
    Object configuration = configurations(saved).get(provider.id());
    if (!(configuration instanceof Map<?, ?> values))
      throw new IllegalArgumentException("Saved provider configuration is missing.");
    var properties = new LinkedHashMap<String, Object>();
    properties.put("spring.ai.model.chat", provider.runtimeId());
    properties.put("zalava.model.provider", provider.id());
    for (var field : provider.fields()) {
      Object value = values.get(field.name());
      if (!(value instanceof String))
        throw new IllegalArgumentException("Saved provider field is missing.");
      properties.put(field.property(), value);
    }
    if (provider.runtimeId().equals("openai")) {
      properties.put("spring.ai.openai.microsoft-foundry", false);
      properties.put("spring.ai.openai.git-hub-models", false);
    }
    if (provider.id().equals("google-genai")) {
      properties.put("spring.ai.google.genai.vertex-ai", false);
      properties.put("spring.ai.google.genai.project-id", "");
      properties.put("spring.ai.google.genai.location", "");
      properties.put("spring.ai.google.genai.credentials-uri", "");
    }
    properties.put("zalava.model.tool-calling-enabled", true);
    properties.putAll(provider.fixedProperties());
    return properties;
  }
}
