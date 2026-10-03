package org.zalava.web.control.adapter.in.http;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.support.ZalavaComponentTest;

@ZalavaComponentTest
class DevelopmentRestApiProfileRestrictionTest {

  private static final Path WORKSPACE = createWorkspace();

  @Autowired private MockMvc mockMvc;

  @Test
  void doesNotExposeDevelopmentRestApiWithoutDevelopmentOrTestProfile() throws Exception {
    mockMvc.perform(get("/api/zalava/modules")).andExpect(status().isNotFound());
  }

  @Test
  void exposesControlUiIndependentlyFromDevelopmentRestApiProfile() throws Exception {
    mockMvc.perform(get("/zalava/control")).andExpect(status().isOk());
  }

  @Test
  void doesNotExposeSourceModuleInstallationControlsWithoutDevelopmentOrTestProfile()
      throws Exception {
    mockMvc
        .perform(
            post("/zalava/control/source-module-installations")
                .param("moduleId", "example")
                .param("indexYaml", "schemaVersion: 1\nmodules: []"))
        .andExpect(status().isNotFound());
  }

  private static Path createWorkspace() {
    try {
      Path workspace = Files.createTempDirectory("zalava-admin-profile-restriction-test-");
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
