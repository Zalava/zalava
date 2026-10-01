package org.zalava.control.adapter.in.actuator;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.support.SeaComponentTest;

@SeaComponentTest
@TestPropertySource(properties = "sea.observability.management-port=8080")
class ProductionRuntimeStatusComponentTest {

  private static final Path WORKSPACE = createWorkspace();

  @Autowired private MockMvc mockMvc;

  @Test
  void exposesLivenessAndReadinessHealth() throws Exception {
    mockMvc
        .perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    mockMvc
        .perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void exposesReleaseAndLoadedModuleStatusWithoutEnvironmentDetails() throws Exception {
    mockMvc
        .perform(get("/actuator/info"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sea.coreVersion").value(System.getProperty("sea.test.version")))
        .andExpect(jsonPath("$.sea.releaseImage").value("sea-local:component"))
        .andExpect(jsonPath("$.sea.releaseRevision").value("component"))
        .andExpect(jsonPath("$.sea.modules").isArray())
        .andExpect(jsonPath("$.env").doesNotExist());
  }

  private static Path createWorkspace() {
    try {
      Path workspace = Files.createTempDirectory("production-runtime-status-test-");
      Files.writeString(workspace.resolve("AGENT.md"), "Test agent prompt.");
      Files.writeString(workspace.resolve("INFO.md"), "Test environment info.");
      Path skill = Files.createDirectories(workspace.resolve("skills/test-skill"));
      Files.writeString(
          skill.resolve("SKILL.md"),
          """
                    ---
                    name: test-skill
                    description: Minimal skill for component test startup.
                    ---
                    # Test Skill
                    """);
      return workspace;
    } catch (IOException ex) {
      throw new ExceptionInInitializerError(ex);
    }
  }
}
