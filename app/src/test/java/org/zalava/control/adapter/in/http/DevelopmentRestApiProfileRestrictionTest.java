package org.zalava.control.adapter.in.http;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.zalava.support.SeaComponentTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@SeaComponentTest
class DevelopmentRestApiProfileRestrictionTest {

  private static final Path WORKSPACE = createWorkspace();

  @Autowired private MockMvc mockMvc;

  @Test
  void doesNotExposeDevelopmentRestApiWithoutDevelopmentOrTestProfile() throws Exception {
    mockMvc.perform(get("/api/sea/modules")).andExpect(status().isNotFound());
  }

  @Test
  void exposesControlUiIndependentlyFromDevelopmentRestApiProfile() throws Exception {
    mockMvc.perform(get("/sea/control")).andExpect(status().isOk());
  }

  @Test
  void doesNotExposeSourceModuleInstallationControlsWithoutDevelopmentOrTestProfile()
      throws Exception {
    mockMvc
        .perform(
            post("/sea/control/source-module-installations")
                .param("moduleId", "example")
                .param("indexYaml", "schemaVersion: 1\nmodules: []"))
        .andExpect(status().isNotFound());
  }

  private static Path createWorkspace() {
    try {
      Path workspace = Files.createTempDirectory("sea-admin-profile-restriction-test-");
      Files.writeString(workspace.resolve("AGENT.md"), "Test agent prompt.");
      Files.writeString(workspace.resolve("INFO.md"), "Test environment info.");
      Path skill = Files.createDirectories(workspace.resolve("skills/test-skill"));
      Files.writeString(
          skill.resolve("SKILL.md"),
          """
                    ---
                    name: test-skill
                    description: Minimal skill for component test context startup.
                    ---

                    # Test Skill
                    """);
      return workspace;
    } catch (IOException ex) {
      throw new ExceptionInInitializerError(ex);
    }
  }
}
