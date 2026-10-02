package org.zalava.knowledge.skills.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkillDescriptorTest {

  @Test
  void buildsAValidDescriptorAndNormalizesLists() {
    SkillDescriptor descriptor = descriptor("project-helper", "1.2.3");

    assertThat(descriptor.hasConcreteVersion()).isTrue();
    assertThat(descriptor.capabilities()).containsExactly("planning", "review");
    assertThat(descriptor.withStatus(SkillStatus.ACTIVE).status()).isEqualTo(SkillStatus.ACTIVE);
  }

  @Test
  void requiresKebabCaseNameAndDescription() {
    assertThatThrownBy(() -> descriptor("Project Helper", "1.0.0"))
        .isInstanceOf(SkillDescriptor.InvalidSkillMetadataException.class)
        .hasMessageContaining("kebab-case");
    assertThatThrownBy(
            () ->
                new SkillDescriptor(
                    "name",
                    "1.0.0",
                    " ",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    null,
                    SkillVisibility.ALL,
                    SkillProvenance.local("name"),
                    SkillStatus.INSTALLED))
        .isInstanceOf(SkillDescriptor.InvalidSkillMetadataException.class)
        .hasMessageContaining("description");
  }

  @Test
  void parsesVisibilityDefaultsAndRejectsUnknownValues() {
    assertThat(SkillVisibility.parse(null)).isEqualTo(SkillVisibility.ALL);
    assertThat(SkillVisibility.parse(" ")).isEqualTo(SkillVisibility.ALL);
    assertThat(SkillVisibility.parse("ADMIN")).isEqualTo(SkillVisibility.ADMIN);
    assertThatThrownBy(() -> SkillVisibility.parse("secret"))
        .isInstanceOf(SkillDescriptor.InvalidSkillMetadataException.class);
  }

  private static SkillDescriptor descriptor(String name, String version) {
    return new SkillDescriptor(
        name,
        version,
        "Helps with project work.",
        List.of(" planning ", "planning", "review"),
        List.of("tasks"),
        List.of(),
        List.of(),
        null,
        SkillVisibility.ALL,
        SkillProvenance.local(name),
        SkillStatus.INSTALLED);
  }
}
