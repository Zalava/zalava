package org.zalava.skills.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.skills.domain.SkillDescriptor;
import org.zalava.skills.domain.SkillProvenance;
import org.zalava.skills.domain.SkillVisibility;

class FileSystemSkillCatalogTest {

  @TempDir Path workspace;

  @Test
  void parsesTheMaintainedSkillFormatWithDefaultVersion() throws IOException {
    write(
        "skill-creator",
        """
        ---
        name: skill-creator
        description: Create and refine skills.
        ---
        # Skill Creator
        """);

    var catalog = new FileSystemSkillCatalog(workspace);

    assertThat(catalog.discover())
        .singleElement()
        .satisfies(
            skill -> {
              assertThat(skill.name()).isEqualTo("skill-creator");
              assertThat(skill.version()).isEqualTo(SkillDescriptor.DEFAULT_VERSION);
              assertThat(skill.status().name()).isEqualTo("INSTALLED");
              assertThat(skill.provenance()).isEqualTo(SkillProvenance.local("skill-creator"));
            });
  }

  @Test
  void parsesRichMetadataIntoAValidatedDescriptor() throws IOException {
    write(
        "shopping-helper",
        """
        ---
        name: shopping-helper
        description: Add and read shopping list items.
        version: 1.2.0
        seaApiVersion: 1.3.0
        visibility: admin
        capabilities:
          - shopping
          - list-management
        recommendedTools: [addItem, listItems]
        policyConstraints:
          - household
        validationSteps: [run focused tests]
        ---
        # Shopping Helper
        """);

    var skill = new FileSystemSkillCatalog(workspace).discover().getFirst();

    assertThat(skill.version()).isEqualTo("1.2.0");
    assertThat(skill.hasConcreteVersion()).isTrue();
    assertThat(skill.capabilities()).containsExactly("shopping", "list-management");
    assertThat(skill.recommendedTools()).containsExactly("addItem", "listItems");
    assertThat(skill.policyConstraints()).containsExactly("household");
    assertThat(skill.validationSteps()).containsExactly("run focused tests");
    assertThat(skill.seaApiVersion()).isEqualTo("1.3.0");
    assertThat(skill.visibility()).isEqualTo(SkillVisibility.ADMIN);
  }

  @Test
  void skipsMalformedOrMismatchedCandidatesInsteadOfFailing() throws IOException {
    write(
        "valid",
        """
        ---
        name: valid
        description: A valid skill.
        ---
        """);
    write(
        "missing-description",
        """
        ---
        name: missing-description
        ---
        """);
    write(
        "mismatch",
        """
        ---
        name: different-name
        description: Name does not match the directory.
        ---
        """);
    write(
        "broken",
        """
        ---
        name: broken
        description: [unterminated
        ---
        """);
    Files.createDirectories(workspace.resolve("no-front-matter"));
    Files.writeString(workspace.resolve("no-front-matter/SKILL.md"), "# No front matter");

    assertThat(new FileSystemSkillCatalog(workspace).discover())
        .extracting(SkillDescriptor::name)
        .containsExactly("valid");
  }

  @Test
  void missingDirectoryYieldsAnEmptyCatalogue() {
    assertThat(new FileSystemSkillCatalog(workspace.resolve("absent")).discover()).isEmpty();
  }

  @Test
  void reloadReturnsTheSameDescriptors() throws IOException {
    write(
        "stable",
        """
        ---
        name: stable
        description: Stable skill.
        version: 0.9.0
        ---
        """);

    assertThat(new FileSystemSkillCatalog(workspace).discover())
        .isEqualTo(new FileSystemSkillCatalog(workspace).discover());
  }

  private void write(String directory, String content) throws IOException {
    Path skillDirectory = Files.createDirectories(workspace.resolve(directory));
    Files.writeString(skillDirectory.resolve("SKILL.md"), content);
  }
}
