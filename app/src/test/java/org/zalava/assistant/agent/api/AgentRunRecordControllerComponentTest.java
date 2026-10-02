package org.zalava.assistant.agent.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.assistant.agent.application.port.out.AgentRunStore;
import org.zalava.assistant.agent.domain.AgentRun;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentRunControllerComponentTest {

  private static final Path WORKSPACE = createWorkspace();

  @Autowired private MockMvc mockMvc;

  @Autowired private TestAgentRunRecorder runRecorder;

  @DynamicPropertySource
  static void testProperties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("agent.browser.brave.api-key", () -> "test-key");
    registry.add("agent.tools.playwright.enabled", () -> "true");
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @BeforeEach
  void resetRecorder() {
    runRecorder.clear();
  }

  @Test
  void listsRecentAgentRunsNewestFirst() throws Exception {
    runRecorder.record(record("run-1", "conversation-1", AgentRun.Status.SUCCEEDED));
    runRecorder.record(record("run-2", "conversation-2", AgentRun.Status.FAILED));

    mockMvc
        .perform(get("/api/agent/runs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].id").value("run-2"))
        .andExpect(jsonPath("$[0].conversationId").value("conversation-2"))
        .andExpect(jsonPath("$[0].promptType").value("STRUCTURED"))
        .andExpect(jsonPath("$[0].promptPreview").value("prompt-run-2"))
        .andExpect(jsonPath("$[0].selectedToolCount").value(2))
        .andExpect(jsonPath("$[0].contextSourceCount").value(1))
        .andExpect(jsonPath("$[0].contextCharacterBudget").value(1000))
        .andExpect(jsonPath("$[0].contextCharactersUsed").value(12))
        .andExpect(jsonPath("$[0].contextSourceMetrics[0].sourceType").value("user_prompt"))
        .andExpect(jsonPath("$[0].contextSourceMetrics[0].charactersAvailable").value(12))
        .andExpect(jsonPath("$[0].contextSourceMetrics[0].charactersUsed").value(12))
        .andExpect(jsonPath("$[0].startedAt").value("2026-06-20T10:00:00Z"))
        .andExpect(jsonPath("$[0].completedAt").value("2026-06-20T10:00:01Z"))
        .andExpect(jsonPath("$[0].durationMillis").value(1000))
        .andExpect(jsonPath("$[0].status").value("FAILED"))
        .andExpect(jsonPath("$[0].resultPreview").value("result-run-2"))
        .andExpect(jsonPath("$[0].errorPreview").value("error-run-2"))
        .andExpect(jsonPath("$[1].id").value("run-1"));
  }

  @Test
  void appliesLimitToRecentAgentRuns() throws Exception {
    runRecorder.record(record("run-1", "conversation-1", AgentRun.Status.SUCCEEDED));
    runRecorder.record(record("run-2", "conversation-2", AgentRun.Status.SUCCEEDED));
    runRecorder.record(record("run-3", "conversation-3", AgentRun.Status.SUCCEEDED));

    mockMvc
        .perform(get("/api/agent/runs").param("limit", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[0].id").value("run-3"))
        .andExpect(jsonPath("$[1].id").value("run-2"));
  }

  @Test
  void rejectsInvalidLimit() throws Exception {
    mockMvc.perform(get("/api/agent/runs").param("limit", "0")).andExpect(status().isBadRequest());

    mockMvc
        .perform(get("/api/agent/runs").param("limit", "201"))
        .andExpect(status().isBadRequest());
  }

  private static AgentRun record(String id, String conversationId, AgentRun.Status status) {
    return new AgentRun(
        id,
        conversationId,
        AgentRun.PromptType.STRUCTURED,
        "prompt-" + id,
        2,
        1,
        1_000,
        12,
        List.of(new AgentRun.ContextSourceMetric("user_prompt", 12, 12)),
        Instant.parse("2026-06-20T10:00:00Z"),
        Instant.parse("2026-06-20T10:00:01Z"),
        1_000,
        status,
        "result-" + id,
        "error-" + id);
  }

  private static Path createWorkspace() {
    try {
      Path workspace = Files.createTempDirectory("agent-run-record-controller-component-test-");
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

  @TestConfiguration(proxyBeanMethods = false)
  static class AgentRunControllerTestConfiguration {

    @Bean
    @Primary
    TestAgentRunRecorder testAgentRunRecorder() {
      return new TestAgentRunRecorder();
    }
  }

  static final class TestAgentRunRecorder implements AgentRunStore {

    private final List<AgentRun> records = new ArrayList<>();

    @Override
    public void record(AgentRun record) {
      records.add(record);
    }

    @Override
    public List<AgentRun> recent() {
      return List.copyOf(records);
    }

    void clear() {
      records.clear();
    }
  }
}
