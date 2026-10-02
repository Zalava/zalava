package org.zalava.modules.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.api.ModuleConfigurationDescriptor;
import org.zalava.api.ModuleConfigurationStatus;

class ModuleConfigurationValidatorTest {
  private final ModuleConfigurationValidator validator = new ModuleConfigurationValidator();

  @Test
  void validatesModuleDocumentAndScopesOneFactory() {
    ModuleConfigurationDescriptor descriptor = descriptor();
    Map<String, Object> document = Map.of("search", Map.of("credentialRef", "brave-key"));

    assertThat(validator.validate(module(descriptor.jsonSchema()), descriptor, document))
        .isEqualTo(ModuleConfigurationStatus.RESTART_REQUIRED);
    assertThat(validator.factoryContext(document, "search").configuration())
        .containsExactly(Map.entry("credentialRef", "brave-key"));
    assertThat(validator.factoryContext(document, "other").configuration()).isEmpty();
  }

  @Test
  void rejectsInvalidOrMismatchedConfigurationContracts() {
    ModuleConfigurationDescriptor descriptor = descriptor();
    assertThatThrownBy(
            () ->
                validator.validate(
                    module(descriptor.jsonSchema()), descriptor, Map.of("search", Map.of())))
        .hasMessageContaining("violates schema");
    assertThatThrownBy(
            () -> validator.validate(module(Map.of("type", "object")), descriptor, Map.of()))
        .hasMessageContaining("schemas differ");
  }

  @Test
  void suppliesOnlyHostResolvedSecretsToTheScopedFactoryContext() {
    var context =
        validator.factoryContext(
            Map.of("search", Map.of()),
            "search",
            reference ->
                reference.equals("brave-key")
                    ? java.util.Optional.of("secret".toCharArray())
                    : java.util.Optional.empty());

    assertThat(context.secrets().resolve("brave-key").map(String::new)).hasValue("secret");
    assertThat(context.secrets().resolve("host-environment")).isEmpty();
  }

  @Test
  void validatesAProductFormDocumentAgainstThePackagedSchema() {
    ModuleConfigurationDescriptor descriptor = descriptor();

    validator.validateDocument(descriptor, Map.of("search", Map.of("credentialRef", "brave-key")));

    assertThatThrownBy(() -> validator.validateDocument(descriptor, Map.of("search", Map.of())))
        .hasMessageContaining("violates schema");
  }

  private static ModuleConfigurationDescriptor descriptor() {
    return new ModuleConfigurationDescriptor(
        Map.of(
            "type",
            "object",
            "properties",
            Map.of(
                "search",
                Map.of(
                    "type",
                    "object",
                    "required",
                    List.of("credentialRef"),
                    "properties",
                    Map.of("credentialRef", Map.of("type", "string")),
                    "additionalProperties",
                    false)),
            "additionalProperties",
            false));
  }

  private static SourceModuleIndex.Module module(Map<String, Object> schema) {
    return new SourceModuleIndex.Module(
        "search-module",
        "1.0.0",
        "Search",
        "Search module",
        URI.create("https://example.test/support"),
        new SourceModuleIndex.Artifact("test", "search", "1.0.0"),
        null,
        new SourceModuleIndex.Build(List.of(), List.of()),
        new SourceModuleIndex.Compatibility(">=1.0.0"),
        schema,
        List.of(new SourceModuleIndex.Factory("search", "search")),
        List.of(),
        new SourceModuleIndex.Security(List.of()));
  }
}
