package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.api.ModuleConfigurationDescriptor;

class ModuleConfigurationFormTest {

  @Test
  void groupsFieldsUsingObjectTitlesAndDescriptionsWithoutChangingSubmittedPaths() {
    var descriptor =
        new ModuleConfigurationDescriptor(
            Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                    "services",
                        Map.of(
                            "type",
                            "object",
                            "title",
                            "Managed services",
                            "description",
                            "Run approved services.",
                            "properties",
                            Map.of("endpoint", Map.of("type", "string", "title", "Connection"))),
                    "containers",
                        Map.of(
                            "type",
                            "object",
                            "title",
                            "Container tools",
                            "properties",
                            Map.of("endpoint", Map.of("type", "string"))))));
    var fields = ModuleConfigurationForm.fields(descriptor, Map.of());
    var sections = ModuleConfigurationForm.sections(fields);
    assertThat(sections)
        .extracting(ModuleConfigurationForm.Section::title)
        .containsExactlyInAnyOrder("Managed services", "Container tools");
    var service =
        sections.stream()
            .filter(section -> "Managed services".equals(section.title()))
            .findFirst()
            .orElseThrow();
    assertThat(service.description()).isEqualTo("Run approved services.");
    assertThat(service.fields())
        .extracting(ModuleConfigurationForm.Field::name)
        .containsExactly("configuration.services.endpoint");
    assertThat(
            ModuleConfigurationForm.document(
                fields, Map.of("configuration.services.endpoint", "local")))
        .isEqualTo(Map.of("services", Map.of("endpoint", "local")));
  }

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
