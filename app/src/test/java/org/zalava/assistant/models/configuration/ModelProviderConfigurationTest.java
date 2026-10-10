package org.zalava.assistant.models.configuration;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.mock.env.MockEnvironment;
import org.zalava.assistant.models.configuration.adapter.out.filesystem.ModelProviderStore;
import org.zalava.assistant.models.configuration.adapter.out.spring.ModelProviderEnvironmentPostProcessor;
import org.zalava.assistant.models.configuration.application.ModelProviderConfiguration;
import org.zalava.assistant.models.configuration.domain.ChatProviderCatalog;
import org.zalava.assistant.models.configuration.domain.ChatProviderCatalog.Provider;

class ModelProviderConfigurationTest {
  @TempDir Path directory;

  static Stream<Provider> providers() {
    return ChatProviderCatalog.providers().stream();
  }

  private ModelProviderStore store() {
    return new ModelProviderStore(directory.resolve("workspace"));
  }

  private MockEnvironment environment() {
    return new MockEnvironment().withProperty("spring.ai.model.chat", "unknown");
  }

  static Map<String, String> valid(Provider provider) {
    var fields = new LinkedHashMap<String, String>();
    for (var field : provider.fields()) {
      String value = field.defaultValue();
      if (field.required() && value.isBlank())
        value = field.name().equals("baseUrl") ? "https://example.test/v1" : "test-" + field.name();
      fields.put(field.name(), value);
    }
    return fields;
  }

  @ParameterizedTest
  @MethodSource("providers")
  void persistsEverySupportedProviderAndMapsActualSpringAiProperties(Provider provider)
      throws Exception {
    var store = store();
    var env = environment();
    var settings = new ModelProviderConfiguration(store, env::getProperty);
    var values = valid(provider);
    settings.save(provider.id(), values);
    assertThat(settings.display(null).provider().id()).isEqualTo(provider.id());
    assertThat(settings.display(null).restartRequired()).isTrue();
    var properties = ModelProviderConfiguration.runtimeProperties(store.read());
    assertThat(properties).containsEntry("spring.ai.model.chat", provider.runtimeId());
    for (var field : provider.fields()) {
      assertThat(properties).containsEntry(field.property(), values.get(field.name()));
      var display =
          settings.display(null).fields().stream()
              .filter(f -> f.name().equals(field.name()))
              .findFirst()
              .orElseThrow();
      assertThat(display.value()).isEqualTo(field.secret() ? "" : values.get(field.name()));
    }
    properties.forEach((key, value) -> env.setProperty(key, value.toString()));
    assertThat(settings.display(null).restartRequired()).isFalse();
    assertThat(Files.getPosixFilePermissions(store.path()))
        .isEqualTo(PosixFilePermissions.fromString("rw-------"));
    assertThat(Files.getPosixFilePermissions(store.path().getParent()))
        .isEqualTo(PosixFilePermissions.fromString("rwx------"));
    assertThat(store.path().startsWith(directory.resolve("workspace"))).isFalse();
  }

  @Test
  void retainsCredentialsOnBlankUpdateWithoutTransferringThemToAnotherProvider() throws Exception {
    var settings = new ModelProviderConfiguration(store(), environment()::getProperty);
    settings.save("openai", valid(ChatProviderCatalog.require("openai")));
    settings.save(
        "openai", Map.of("model", "replacement-model", "baseUrl", "https://example.test/v1"));
    assertThat(ModelProviderConfiguration.runtimeProperties(store().read()))
        .containsEntry("spring.ai.openai.api-key", "test-apiKey");
    assertThat(
            settings.display(null).fields().stream()
                .filter(f -> f.name().equals("apiKey"))
                .findFirst()
                .orElseThrow()
                .savedSecret())
        .isTrue();
    assertThatThrownBy(
            () ->
                settings.save(
                    "groq", Map.of("model", "other-model", "baseUrl", "https://example.test/v1")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Enter api key.");
    assertThat(settings.display(null).provider().id()).isEqualTo("openai");
  }

  @Test
  void rejectsInvalidFieldsWithoutOverwritingPreviousConfiguration() throws Exception {
    var settings = new ModelProviderConfiguration(store(), environment()::getProperty);
    var values = valid(ChatProviderCatalog.require("openai"));
    settings.save("openai", values);
    var before = store().read();
    for (String endpoint :
        new String[] {
          "bad",
          "file:/secret",
          "https://user:secret@example.test",
          "https://example.test/#fragment"
        }) {
      values.put("baseUrl", endpoint);
      assertThatThrownBy(() -> settings.save("openai", values))
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(store().read()).isEqualTo(before);
    }
    values.put("baseUrl", "https://example.test");
    values.put("model", " ");
    assertThatThrownBy(() -> settings.save("openai", values)).hasMessage("Enter model.");
    values.put("model", "x".repeat(8193));
    assertThatThrownBy(() -> settings.save("openai", values))
        .isInstanceOf(IllegalArgumentException.class);
    values.put("model", "two\nlines");
    assertThatThrownBy(() -> settings.save("openai", values))
        .isInstanceOf(IllegalArgumentException.class);
    values.put("model", "two\rlines");
    assertThatThrownBy(() -> settings.save("openai", values))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> settings.save("unknown", values))
        .hasMessage("Choose a supported provider.");
  }

  @Test
  void validatesCloudCredentialModes() throws Exception {
    var settings = new ModelProviderConfiguration(store(), environment()::getProperty);
    var vertex = valid(ChatProviderCatalog.require("google-vertex"));
    vertex.put("credentialsUri", "https://example.test/secret");
    assertThatThrownBy(() -> settings.save("google-vertex", vertex))
        .isInstanceOf(IllegalArgumentException.class);
    vertex.put("credentialsUri", "file:/run/secrets/google.json");
    settings.save("google-vertex", vertex);
    assertThat(ModelProviderConfiguration.runtimeProperties(store().read()))
        .containsEntry("spring.ai.google.genai.vertex-ai", true);
    var aws = valid(ChatProviderCatalog.require("bedrock-converse"));
    aws.put("accessKey", "key");
    assertThatThrownBy(() -> settings.save("bedrock-converse", aws))
        .hasMessageContaining("both AWS");
    aws.put("secretKey", "secret");
    settings.save("bedrock-converse", aws);
  }

  @Test
  void readsExistingEnvironmentAndLegacyModelPropertyWithoutShowingSecrets() throws Exception {
    var env =
        environment()
            .withProperty("spring.ai.model.chat", "openai")
            .withProperty("spring.ai.openai.api-key", "environment-secret")
            .withProperty("spring.ai.openai.chat.options.model", "existing-model");
    var settings = new ModelProviderConfiguration(store(), env::getProperty);
    assertThat(
            settings.display(null).fields().stream()
                .filter(f -> f.name().equals("model"))
                .map(f -> f.value())
                .toList())
        .containsExactly("existing-model");
    settings.save("openai", Map.of("model", "replacement", "baseUrl", "https://example.test"));
    assertThat(ModelProviderConfiguration.runtimeProperties(store().read()))
        .containsEntry("spring.ai.openai.api-key", "environment-secret");
    assertThat(settings.display("ollama").fields()).noneMatch(f -> f.savedSecret());
  }

  @Test
  void loadsOnlyWhitelistedSavedPropertiesBeforeAutoConfiguration() throws Exception {
    var env =
        environment()
            .withProperty("agent.workspace", directory.resolve("workspace").toUri().toString());
    var settings = new ModelProviderConfiguration(store(), env::getProperty);
    settings.save("deepseek", valid(ChatProviderCatalog.require("deepseek")));
    var processor = new ModelProviderEnvironmentPostProcessor();
    processor.postProcessEnvironment(env, new SpringApplication());
    assertThat(env.getProperty("spring.ai.model.chat")).isEqualTo("deepseek");
    assertThat(env.getProperty("spring.ai.deepseek.chat.model")).isEqualTo("test-model");
    assertThat(processor.getOrder()).isGreaterThan(ConfigDataEnvironmentPostProcessor.ORDER);
    assertThat(settings.display(null).restartRequired()).isFalse();
  }

  @Test
  void missingAndCorruptSettingsAreHandledWithoutLeakingContents() throws Exception {
    var env =
        environment()
            .withProperty("agent.workspace", directory.resolve("workspace").toUri().toString());
    var processor = new ModelProviderEnvironmentPostProcessor();
    processor.postProcessEnvironment(env, new SpringApplication());
    assertThat(env.getProperty("spring.ai.model.chat")).isEqualTo("unknown");
    store().write(Map.of("provider", "unknown", "secret", "do-not-echo"));
    assertThatThrownBy(() -> processor.postProcessEnvironment(env, new SpringApplication()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unable to load saved model provider configuration.")
        .hasNoCause();
    assertThat(ModelProviderConfiguration.runtimeProperties(Map.of())).isEmpty();
    assertThatThrownBy(
            () -> ModelProviderConfiguration.runtimeProperties(Map.of("provider", "openai")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                ModelProviderConfiguration.runtimeProperties(
                    Map.of("provider", "openai", "configurations", Map.of("openai", Map.of()))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void invalidPrivateYamlNeverAppearsInDiagnostics() throws Exception {
    var store = store();
    store.write(Map.of());
    for (String document :
        new String[] {"secret-content", "1: secret-content", "apiKey: [secret-content"}) {
      Files.writeString(store.path(), document);
      assertThatThrownBy(store::read)
          .isInstanceOf(IOException.class)
          .hasMessage("Saved model provider settings are invalid.")
          .hasNoCause();
    }
    Files.writeString(store.path(), "");
    assertThat(store.read()).isEmpty();
  }

  @Test
  void refusesSymlinkCredentialStorage() throws Exception {
    Path target = Files.createDirectory(directory.resolve("other"));
    Files.createSymbolicLink(store().path().getParent(), target);
    assertThatThrownBy(() -> store().read()).isInstanceOf(IOException.class);
    assertThatThrownBy(() -> store().write(Map.of())).isInstanceOf(IOException.class);
  }
}
