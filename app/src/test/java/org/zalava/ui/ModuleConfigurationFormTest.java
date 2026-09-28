package org.zalava.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.zalava.ModuleConfigurationDescriptor;
import org.junit.jupiter.api.Test;

class ModuleConfigurationFormTest {

  @Test
  void preservesARequiredBooleanFieldWhenTheCheckboxIsNotSelected() {
    ModuleConfigurationDescriptor descriptor =
        new ModuleConfigurationDescriptor(
            Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                    "factory",
                    Map.of(
                        "type", "object",
                        "properties", Map.of("enabled", Map.of("type", "boolean")),
                        "required", List.of("enabled")))));

    assertThat(
            ModuleConfigurationForm.document(
                ModuleConfigurationForm.fields(descriptor, Map.of()), Map.of()))
        .isEqualTo(Map.of("factory", Map.of("enabled", false)));
  }

  @Test
  void parsesStructuredFieldsFromJson() {
    ModuleConfigurationDescriptor descriptor =
        new ModuleConfigurationDescriptor(
            Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                    "factory",
                    Map.of(
                        "type", "object",
                        "properties", Map.of("roots", Map.of("type", "array")),
                        "required", List.of("roots")))));

    assertThat(
            ModuleConfigurationForm.document(
                ModuleConfigurationForm.fields(descriptor, Map.of()),
                Map.of("configuration.factory.roots", "[{\"id\":\"workspace\"}]")))
        .isEqualTo(Map.of("factory", Map.of("roots", List.of(Map.of("id", "workspace")))));
  }

  @Test
  void rejectsMalformedStructuredFieldJson() {
    ModuleConfigurationDescriptor descriptor =
        new ModuleConfigurationDescriptor(
            Map.of("type", "object", "properties", Map.of("roots", Map.of("type", "array"))));

    assertThatThrownBy(
            () ->
                ModuleConfigurationForm.document(
                    ModuleConfigurationForm.fields(descriptor, Map.of()),
                    Map.of("configuration.roots", "[")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("roots must be valid JSON.");
  }
}
