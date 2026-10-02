package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.stringContainsInOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.assistant.agent.application.port.out.AgentRunStore;
import org.zalava.assistant.agent.domain.AgentRun;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.support.AuthenticatedSeaComponentTest;
import org.zalava.support.ComponentTestAccounts;
import org.zalava.tasks.application.port.in.TaskQueries;
import org.zalava.tasks.domain.RecurringTask;
import org.zalava.tasks.domain.Task;
import org.zalava.tasks.domain.TaskReference;
import org.zalava.web.control.application.port.in.BootstrapVerificationQueries;
import org.zalava.web.control.application.port.in.BootstrapVerificationQueries.BootstrapToolVerification;
import org.zalava.web.control.application.port.in.InvocationLogQueries;
import org.zalava.web.control.application.port.in.InvocationLogQueries.Entry;

/**
 * Full-context MockMvc component test for the administrator Monitoring screen. It drives the real
 * controller and filter chain over isolated in-memory evidence ports, proving bounds, empty states,
 * evidence links and the administrator-only authority boundary without touching the shared
 * component workspace.
 */
@AuthenticatedSeaComponentTest
class MonitoringControllerComponentTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private InMemoryAgentRunStore runStore;
  @Autowired private RecordingInvocationLog invocationLog;
  @Autowired private StubBootstrapVerification bootstrapVerification;
  @Autowired private StubTaskQueries taskQueries;
  @Autowired private ComponentTestAccounts accounts;

  @BeforeEach
  void cleanEvidence() {
    runStore.clear();
    taskQueries.clear();
    invocationLog.clear();
    bootstrapVerification.clear();
  }

  @Test
  void rendersEmptyMonitoringSnapshot() throws Exception {
    mockMvc
        .perform(get("/monitoring"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("<title>Zalava Monitoring</title>")))
        .andExpect(content().string(containsString("aria-current=\"page\" href=\"/monitoring\"")))
        .andExpect(content().string(containsString("data-monitoring-snapshot")))
        .andExpect(content().string(containsString("data-refreshed-at")))
        .andExpect(content().string(containsString("data-empty=\"live-jobs\"")))
        .andExpect(content().string(containsString("data-empty=\"terminal-jobs\"")))
        .andExpect(content().string(containsString("data-empty=\"runs\"")))
        .andExpect(content().string(containsString("data-empty=\"provider-calls\"")))
        .andExpect(content().string(containsString("data-empty=\"providers\"")))
        .andExpect(content().string(not(containsString("data-metrics-viewer"))));
  }

  @Test
  void rendersLiveTerminalRunAndProviderEvidence() throws Exception {
    Instant now = Instant.now();
    taskQueries.register(
        new Task(null, "Running build", now, Task.Status.in_progress, "compile"),
        TaskReference.parse("2026-09-18", "081850-Running_build.md"));
    taskQueries.register(
        new Task(
            null, "Finished build", now.minus(1, ChronoUnit.HOURS), Task.Status.completed, "done"),
        TaskReference.parse("2026-09-18", "071850-Finished_build.md"));

    String taskId = UUID.randomUUID().toString();
    String accountId = UUID.randomUUID().toString();
    runStore.record(
        run(
            "run-1",
            accountId + ":" + taskId,
            AgentRun.Status.SUCCEEDED,
            "old prompt",
            now.minusSeconds(30)));
    runStore.record(
        run(
            "run-2",
            "conversation-alpha",
            AgentRun.Status.SUCCEEDED,
            "new prompt",
            now.minusSeconds(20)));
    runStore.record(
        run(
            "run-3",
            accountId + ":" + taskId,
            AgentRun.Status.FAILED,
            "failed prompt",
            now.minusSeconds(10)));

    invocationLog.record(call("run-ok", true, null, "result-ok", 120));
    invocationLog.record(call("run-bad", false, "ProviderTimeout", "result-bad", 250));
    bootstrapVerification.register(
        new BootstrapToolVerification(
            "test-toolset", "provider-a", "Provider A", true, null, List.of()));
    bootstrapVerification.register(
        new BootstrapToolVerification(
            "test-toolset",
            "provider-b",
            "Provider B",
            false,
            "Missing tools: browser",
            List.of()));

    mockMvc
        .perform(get("/monitoring"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("data-job")))
        .andExpect(content().string(containsString("Running build")))
        .andExpect(content().string(containsString("Finished build")))
        .andExpect(
            content().string(containsString("href=\"/jobs/2026-09-18/081850-Running_build.md\"")))
        .andExpect(content().string(containsString("data-run-id=\"run-3\"")))
        .andExpect(content().string(containsString("data-run-failed=\"true\"")))
        .andExpect(content().string(containsString("href=\"/jobs/" + taskId + "\"")))
        .andExpect(content().string(containsString("View job evidence")))
        .andExpect(content().string(containsString("href=\"/chat\"")))
        .andExpect(content().string(containsString("ProviderTimeout")))
        .andExpect(content().string(containsString("Missing tools: browser")))
        .andExpect(content().string(stringContainsInOrder("run-3", "run-2", "run-1")));
  }

  @Test
  void boundsRunListAndProviderPreviews() throws Exception {
    Instant now = Instant.now();
    for (int index = 0; index < 21; index++) {
      runStore.record(
          run(
              "run-" + index,
              "conversation-" + index,
              AgentRun.Status.SUCCEEDED,
              "prompt-" + index,
              now.plusSeconds(index)));
    }
    String longResult = "R".repeat(300) + "TAIL-MARKER";
    invocationLog.record(call("tool", true, null, longResult, 10));

    String body =
        mockMvc
            .perform(get("/monitoring"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body.split("data-run-id=", -1)).hasSize(21);
    assertThat(body).contains("R".repeat(200) + "...");
    assertThat(body).doesNotContain("TAIL-MARKER");
  }

  @Test
  void boundsRunPreviewsToTheDomainLimit() throws Exception {
    String overlong = "P".repeat(600) + "PROMPT-TAIL";
    runStore.record(
        run("run-long", "conversation-long", AgentRun.Status.SUCCEEDED, overlong, Instant.now()));

    String body =
        mockMvc
            .perform(get("/monitoring"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body).contains("P".repeat(500));
    assertThat(body).doesNotContain("PROMPT-TAIL");
  }

  @Test
  void restrictsMonitoringToAdministrators() throws Exception {
    Account member = accounts.newActivated(AccountRole.MEMBER);

    mockMvc
        .perform(get("/monitoring").with(accounts.authenticatedAs(member)))
        .andExpect(status().isForbidden());

    mockMvc.perform(get("/monitoring").with(anonymous())).andExpect(status().is3xxRedirection());
  }

  private static AgentRun run(
      String id, String conversationId, AgentRun.Status status, String prompt, Instant startedAt) {
    return new AgentRun(
        id,
        conversationId,
        AgentRun.PromptType.CONVERSATIONAL,
        prompt,
        0,
        0,
        0,
        0,
        List.of(),
        startedAt,
        startedAt.plusMillis(50),
        50,
        status,
        "result-" + id,
        status == AgentRun.Status.FAILED ? "error-" + id : null);
  }

  private static Entry call(
      String toolName,
      boolean success,
      String errorType,
      String resultPreview,
      long durationMillis) {
    return new Entry(
        Instant.now().toString(),
        "provider-a",
        toolName,
        null,
        false,
        "read",
        List.of(),
        false,
        success,
        errorType,
        errorType == null ? null : errorType + " detail",
        resultPreview,
        durationMillis);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class MonitoringEvidenceConfiguration {

    @Bean
    @Primary
    InMemoryAgentRunStore inMemoryAgentRunStore() {
      return new InMemoryAgentRunStore();
    }

    @Bean
    @Primary
    RecordingInvocationLog recordingInvocationLog() {
      return new RecordingInvocationLog();
    }

    @Bean
    @Primary
    StubBootstrapVerification stubBootstrapVerification() {
      return new StubBootstrapVerification();
    }

    @Bean
    @Primary
    StubTaskQueries stubTaskQueries() {
      return new StubTaskQueries();
    }
  }

  static final class InMemoryAgentRunStore implements AgentRunStore {

    private final List<AgentRun> runs = new ArrayList<>();

    @Override
    public void record(AgentRun run) {
      runs.add(run);
    }

    @Override
    public List<AgentRun> recent() {
      return List.copyOf(runs);
    }

    void clear() {
      runs.clear();
    }
  }

  static final class RecordingInvocationLog implements InvocationLogQueries {

    private final List<Entry> entries = new ArrayList<>();

    void record(Entry entry) {
      entries.add(0, entry);
    }

    void clear() {
      entries.clear();
    }

    @Override
    public List<Entry> recentEntries() {
      return List.copyOf(entries);
    }
  }

  static final class StubBootstrapVerification implements BootstrapVerificationQueries {

    private final List<BootstrapToolVerification> providers = new ArrayList<>();

    void register(BootstrapToolVerification provider) {
      providers.add(provider);
    }

    void clear() {
      providers.clear();
    }

    @Override
    public List<BootstrapToolVerification> bootstrapVerification() {
      return List.copyOf(providers);
    }
  }

  static final class StubTaskQueries implements TaskQueries {

    private final List<Task> tasks = new ArrayList<>();
    private final List<TaskReference> references = new ArrayList<>();

    void register(Task task, TaskReference reference) {
      tasks.add(task);
      references.add(reference);
    }

    void clear() {
      tasks.clear();
      references.clear();
    }

    @Override
    public Task getTask(TaskReference reference) {
      throw new UnsupportedOperationException();
    }

    @Override
    public TaskReference getReference(Task task) {
      return references.get(tasks.indexOf(task));
    }

    @Override
    public List<Task> getTasks(LocalDate localDate, Task.Status status) {
      return allMatching(status);
    }

    @Override
    public List<Task> getAllTasks() {
      return List.copyOf(tasks);
    }

    @Override
    public List<RecurringTask> getAllRecurringTasks() {
      return List.of();
    }

    private List<Task> allMatching(Task.Status status) {
      return tasks.stream().filter(task -> status == null || task.getStatus() == status).toList();
    }
  }
}
