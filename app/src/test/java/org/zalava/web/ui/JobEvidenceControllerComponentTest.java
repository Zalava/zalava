package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.*;
import org.zalava.tasks.application.port.out.*;
import org.zalava.tasks.domain.*;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.context.annotation.Import(
    JobEvidenceControllerComponentTest.ModelConfiguration.class)
class JobEvidenceControllerComponentTest {
  static final Path WORKSPACE = workspace();
  static final AtomicInteger LOGINS = new AtomicInteger();
  @Autowired MockMvc mvc;
  @Autowired AccountLifecycle accounts;
  @Autowired ActorTaskStore tasks;
  @MockitoBean ActorTaskSchedules schedules;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    org.zalava.support.PostgreSqlTestDatabase.register(registry);
    registry.add("sea.accounts.security-enabled", () -> "true");
    registry.add("sea.accounts.bootstrap-login", () -> "job-evidence-component-admin");
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("agent.channels.telegram.token", () -> "false");
    registry.add("agent.channels.telegram.username", () -> "false");
    registry.add("spring.ai.model.chat", () -> "none");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
  }

  @Test
  void servesOnlyOwnedPersistedReportsAndSchedulesThroughHttp() throws Exception {
    Account owner = member();
    Account other = member();
    Actor actor = new Actor(owner.id());
    var report = ActorTaskReference.newReference();
    var pending = ActorTaskReference.newReference();
    String saved = "# Persisted report\nExact output <script>not executable</script>.";
    tasks.save(
        actor,
        report,
        Task.newTask("Owner report", "Goal")
            .withStatus(Task.Status.failed)
            .withFeedback(saved)
            .withFailureDetail("Recorded failure"));
    tasks.save(actor, pending, Task.newTask("Owner future job", "Goal"));
    tasks.save(
        new Actor(other.id()),
        ActorTaskReference.newReference(),
        Task.newTask("Foreign report", "Goal").withFeedback("Other account secret"));
    Instant due = Instant.parse("2050-01-02T12:00:00Z");
    when(schedules.list(actor))
        .thenReturn(List.of(new ScheduledActorTask(UUID.randomUUID(), pending, due)));
    mvc.perform(get("/jobs").with(user(owner.loginName()).roles("MEMBER")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Owner report")))
        .andExpect(content().string(containsString("2050-01-02T12:00:00Z")))
        .andExpect(
            content().string(org.hamcrest.Matchers.not(containsString("Other account secret"))));
    mvc.perform(get("/dashboard").with(user(owner.loginName()).roles("MEMBER")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Upcoming scheduled jobs")));
    mvc.perform(
            get("/jobs/" + report.value() + "/artifacts/report")
                .with(user(owner.loginName()).roles("MEMBER")))
        .andExpect(status().isOk())
        .andExpect(content().string(saved))
        .andExpect(content().contentTypeCompatibleWith("text/markdown"))
        .andExpect(header().string("Content-Disposition", containsString("attachment")))
        .andExpect(header().string("Cache-Control", "no-store"));
    mvc.perform(
            get("/jobs/" + report.value() + "/artifacts/report")
                .with(user(other.loginName()).roles("MEMBER")))
        .andExpect(status().isNotFound());
    mvc.perform(
            get("/jobs/" + pending.value() + "/artifacts/report")
                .with(user(owner.loginName()).roles("MEMBER")))
        .andExpect(status().isNotFound());
    mvc.perform(get("/jobs/" + report.value() + "/artifacts/report"))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  void directErrorRequestsRemainProtected() throws Exception {
    Account owner = member();
    mvc.perform(get("/error").with(user(owner.loginName()).roles("MEMBER")))
        .andExpect(status().isForbidden());
  }

  @Test
  void rendersUnavailableScheduleSeparatelyFromAnEmptySchedule() throws Exception {
    Account owner = member();
    Actor actor = new Actor(owner.id());
    when(schedules.list(actor)).thenReturn(List.of());
    mvc.perform(get("/jobs").with(user(owner.loginName()).roles("MEMBER")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("No upcoming scheduled jobs.")))
        .andExpect(content().string(containsString("No saved job reports.")));
    when(schedules.list(actor))
        .thenThrow(new TaskScheduleUnavailableException(new IllegalStateException("Offline")));
    mvc.perform(get("/jobs").with(user(owner.loginName()).roles("MEMBER")))
        .andExpect(status().isOk())
        .andExpect(
            content().string(containsString("Scheduled work is unavailable. Reload to try again.")))
        .andExpect(
            content()
                .string(org.hamcrest.Matchers.not(containsString("No upcoming scheduled jobs."))));
  }

  private Account member() {
    Account account =
        accounts.create(
            "job-evidence-member-" + LOGINS.incrementAndGet(),
            "ComponentPassword-123",
            AccountRole.MEMBER);
    accounts.changePassword(account.id(), "ComponentPassword-123", "ComponentPassword-456");
    return account;
  }

  @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
  static class ModelConfiguration {
    @org.springframework.context.annotation.Bean
    org.springframework.ai.chat.model.ChatModel model() {
      return mock(org.springframework.ai.chat.model.ChatModel.class);
    }
  }

  private static Path workspace() {
    try {
      return Files.createTempDirectory("zalava-job-evidence-");
    } catch (java.io.IOException ex) {
      throw new IllegalStateException(ex);
    }
  }
}
