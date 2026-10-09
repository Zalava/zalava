package org.zalava.web.ui;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.support.ZalavaComponentTest;

@ZalavaComponentTest
class LocalControlUiComponentTest {

  private static final Path WORKSPACE = createWorkspace();

  @Autowired private MockMvc mockMvc;

  @Test
  void exposesControlUiWithoutEnablingAdminRestApi() throws Exception {
    mockMvc
        .perform(get("/zalava/control"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Advanced settings")))
        .andExpect(content().string(not(containsString("href=\"/zalava/control/metrics\""))))
        .andExpect(content().string(containsString("External module development")))
        .andExpect(content().string(containsString("Refresh catalog")))
        .andExpect(content().string(containsString("Catalog module")))
        .andExpect(content().string(not(containsString("Catalog module id"))))
        .andExpect(content().string(not(containsString("hx-trigger=\"every 15s\""))));

    mockMvc.perform(get("/api/zalava/modules")).andExpect(status().isNotFound());
  }

  private static Path createWorkspace() {
    try {
      Path workspace = Files.createTempDirectory("local-control-ui-component-test-");
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
