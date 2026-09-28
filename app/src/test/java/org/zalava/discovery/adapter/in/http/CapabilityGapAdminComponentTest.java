package org.zalava.discovery.adapter.in.http;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.zalava.discovery.CapabilityGapClassification;
import org.zalava.discovery.RemoteModuleCandidate;
import org.zalava.discovery.application.port.in.RemoteCapabilityDiscovery;
import org.zalava.discovery.application.port.out.RemoteModuleCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(CapabilityGapAdminComponentTest.CapabilityGapTestConfiguration.class)
class CapabilityGapAdminComponentTest {

  private static final Path WORKSPACE = createWorkspace();

  @Autowired private MockMvc mockMvc;
  @Autowired private RemoteCapabilityDiscovery remoteDiscovery;

  @DynamicPropertySource
  static void testProperties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
    registry.add("sea.discovery.remote.enabled", () -> "true");
    registry.add(
        "sea.discovery.remote.module-locator-url", () -> "https://catalog.example/catalog.yaml");
  }

  @Test
  void exposesPersistedWeakMatchEvidenceWithoutRawPromptText() throws Exception {
    remoteDiscovery.discover("forecast pollen", 0);

    mockMvc
        .perform(get("/api/sea/capability-gap/evidence"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].classification").value("weak_match"))
        .andExpect(jsonPath("$[0].queryDigest", startsWith("sha256:")))
        .andExpect(jsonPath("$[0].installedMatchCount").value(0))
        .andExpect(jsonPath("$[0].candidates[0].moduleId").value("sea-module-weather"))
        .andExpect(jsonPath("$[0].candidates[0].rank").value(1))
        .andExpect(content().string(not(containsString("forecast pollen"))));
  }

  @Test
  void supportsInstalledMatchPrecedenceWithoutRemoteEvidence() throws Exception {
    RemoteCapabilityDiscovery.Outcome outcome = remoteDiscovery.discover("forecast pollen", 2);

    org.assertj.core.api.Assertions.assertThat(outcome.classification())
        .isEqualTo(CapabilityGapClassification.INSTALLED_MATCH);
  }

  @Test
  void rejectsAnOutOfRangeEvidenceLimit() throws Exception {
    mockMvc
        .perform(get("/api/sea/capability-gap/evidence").param("limit", "0"))
        .andExpect(status().isBadRequest());
  }

  private static Path createWorkspace() {
    try {
      Path workspace = Files.createTempDirectory("sea-discovery-component-workspace-");
      Files.writeString(workspace.resolve("AGENT.md"), "Test agent prompt.");
      Files.writeString(workspace.resolve("INFO.md"), "Test environment info.");
      return workspace;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class CapabilityGapTestConfiguration {
    @Bean
    @Primary
    RemoteModuleCatalog deterministicRemoteModuleCatalog() {
      return new RemoteModuleCatalog() {
        @Override
        public boolean configured() {
          return true;
        }

        @Override
        public List<RemoteModuleCandidate> lookup(String normalizedQuery) {
          return List.of(
              new RemoteModuleCandidate(
                  "sea-module-weather",
                  "1.0.0",
                  "d".repeat(64),
                  "Weather",
                  "Pollen forecast",
                  List.of("pollen.read")));
        }
      };
    }
  }
}
