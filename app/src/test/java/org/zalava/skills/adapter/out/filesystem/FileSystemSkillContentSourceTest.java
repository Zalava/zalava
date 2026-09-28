package org.zalava.skills.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemSkillContentSourceTest {

  @TempDir Path workspace;

  @Test
  void loadsTheMarkdownBodyWithoutFrontMatter() throws Exception {
    Path skill = Files.createDirectories(workspace.resolve("skills/test-skill"));
    Files.writeString(
        skill.resolve("SKILL.md"),
        """
        ---
        name: test-skill
        description: A skill.
        ---
        # Test Skill

        Do the thing.
        """);
    var source = new FileSystemSkillContentSource(workspace.resolve("skills"));

    assertThat(source.load("test-skill")).contains("# Test Skill\n\nDo the thing.");
  }

  @Test
  void returnsTheWholeFileWhenThereIsNoFrontMatter() throws Exception {
    Path skill = Files.createDirectories(workspace.resolve("skills/test-skill"));
    Files.writeString(skill.resolve("SKILL.md"), "# Plain Skill\n");
    var source = new FileSystemSkillContentSource(workspace.resolve("skills"));

    assertThat(source.load("test-skill")).contains("# Plain Skill");
  }

  @Test
  void rejectsPathTraversalAndUnknownSkills() {
    var source = new FileSystemSkillContentSource(workspace.resolve("skills"));

    assertThat(source.load("../etc/passwd")).isEmpty();
    assertThat(source.load("Unknown_Skill")).isEmpty();
    assertThat(source.load("missing-skill")).isEmpty();
    assertThat(source.load(null)).isEmpty();
  }

  @Test
  void returnsEmptyForABlankBody() throws Exception {
    Path skill = Files.createDirectories(workspace.resolve("skills/test-skill"));
    Files.writeString(
        skill.resolve("SKILL.md"),
        """
        ---
        name: test-skill
        description: A skill.
        ---
        """);
    var source = new FileSystemSkillContentSource(workspace.resolve("skills"));

    assertThat(source.load("test-skill")).isEmpty();
  }
}
