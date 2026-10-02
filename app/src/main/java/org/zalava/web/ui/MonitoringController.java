package org.zalava.web.ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.zalava.assistant.agent.application.port.in.AgentRunQueries;
import org.zalava.assistant.agent.domain.AgentRun;
import org.zalava.tasks.application.port.in.TaskQueries;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.Task;
import org.zalava.tasks.domain.TaskReference;
import org.zalava.web.control.application.port.in.BootstrapVerificationQueries;
import org.zalava.web.control.application.port.in.InvocationLogQueries;

/**
 * Read-only operational monitoring over evidence SEA already records: live and terminal jobs,
 * persisted agent-run evidence, the bounded provider/tool invocation audit and provider readiness.
 * Cross-actor run and provider evidence keeps this screen administrator-only (see {@code
 * AccountConfiguration}); it composes existing read-only ports and adds no instrumentation.
 */
@Controller
public class MonitoringController {

  private static final int JOB_LIMIT = 10;
  private static final int RUN_LIMIT = 20;
  private static final int CALL_LIMIT = 10;
  private static final int PROVIDER_LIMIT = 20;
  private static final int PROVIDER_PREVIEW_LIMIT = 200;

  private static final DateTimeFormatter SNAPSHOT_TIME =
      DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ENGLISH);
  private static final DateTimeFormatter STARTED_AT =
      DateTimeFormatter.ofPattern("MMM d, HH:mm:ss", Locale.ENGLISH);
  private static final Pattern UUID = Pattern.compile("[0-9a-fA-F-]{36}");

  private final TaskQueries taskQueries;
  private final AgentRunQueries agentRuns;
  private final InvocationLogQueries invocationLog;
  private final BootstrapVerificationQueries bootstrapVerification;
  private final ObservabilitySettings observability;

  public MonitoringController(
      TaskQueries taskQueries,
      AgentRunQueries agentRuns,
      InvocationLogQueries invocationLog,
      BootstrapVerificationQueries bootstrapVerification,
      ObservabilitySettings observability) {
    this.taskQueries = taskQueries;
    this.agentRuns = agentRuns;
    this.invocationLog = invocationLog;
    this.bootstrapVerification = bootstrapVerification;
    this.observability = observability;
  }

  @GetMapping("/monitoring")
  public String monitoring(Model model) {
    Instant now = Instant.now();
    List<Task> tasks = taskQueries.getAllTasks();
    List<Task> liveTasks =
        tasks.stream()
            .filter(task -> isLive(task.getStatus()))
            .sorted(Comparator.comparing(Task::getCreatedAt).reversed())
            .toList();
    List<Task> terminalTasks =
        tasks.stream()
            .filter(task -> !isLive(task.getStatus()))
            .sorted(Comparator.comparing(Task::getCreatedAt).reversed())
            .toList();

    List<AgentRun> allRuns = agentRuns.recent();
    List<AgentRun> runs =
        allRuns.stream()
            .sorted(
                Comparator.comparing(AgentRun::startedAt)
                    .reversed()
                    .thenComparing(AgentRun::id, Comparator.reverseOrder()))
            .limit(RUN_LIMIT)
            .toList();

    List<InvocationLogQueries.Entry> allCalls = invocationLog.recentEntries();
    List<InvocationLogQueries.Entry> calls = allCalls.stream().limit(CALL_LIMIT).toList();

    List<BootstrapVerificationQueries.BootstrapToolVerification> providers =
        bootstrapVerification.bootstrapVerification().stream().limit(PROVIDER_LIMIT).toList();

    Snapshot snapshot =
        new Snapshot(
            liveTasks.size(),
            allRuns.size(),
            (int) allRuns.stream().filter(run -> run.status() == AgentRun.Status.FAILED).count(),
            allCalls.size(),
            (int) allCalls.stream().filter(call -> !call.success()).count());

    model.addAttribute(
        "model",
        new MonitoringModel(
            SNAPSHOT_TIME.format(now.atZone(ZoneId.systemDefault())),
            snapshot,
            liveTasks.stream().limit(JOB_LIMIT).map(this::toJobEntry).toList(),
            terminalTasks.stream().limit(JOB_LIMIT).map(this::toJobEntry).toList(),
            runs.stream().map(MonitoringController::toRunEntry).toList(),
            calls.stream().map(MonitoringController::toCallEntry).toList(),
            providers.stream().map(MonitoringController::toProviderEntry).toList(),
            observability.viewerConfigured(),
            observability.viewerUrl()));
    return "ui/monitoring";
  }

  private JobEntry toJobEntry(Task task) {
    TaskReference reference = taskQueries.getReference(task);
    return new JobEntry(
        task.getName(),
        statusLabel(task.getStatus()),
        statusClass(task.getStatus()),
        statusDetail(task.getStatus()),
        "/jobs/" + reference.path());
  }

  private static RunEntry toRunEntry(AgentRun run) {
    String[] evidence = evidence(run.conversationId());
    return new RunEntry(
        run.id(),
        run.promptType() == AgentRun.PromptType.STRUCTURED ? "Structured" : "Conversational",
        run.status() == AgentRun.Status.FAILED ? "Failed" : "Succeeded",
        run.status() == AgentRun.Status.FAILED ? "is-danger is-light" : "is-success is-light",
        run.status() == AgentRun.Status.FAILED,
        STARTED_AT.format(run.startedAt().atZone(ZoneId.systemDefault())),
        durationLabel(run.durationMillis()),
        run.contextCharactersUsed(),
        run.contextCharacterBudget(),
        run.promptPreview(),
        run.resultPreview(),
        run.errorPreview(),
        evidence[0],
        evidence[1]);
  }

  private static CallEntry toCallEntry(InvocationLogQueries.Entry call) {
    return new CallEntry(
        call.timestamp(),
        call.providerId(),
        call.toolName(),
        call.success() ? "Succeeded" : "Failed",
        call.success() ? "is-success is-light" : "is-danger is-light",
        !call.success(),
        call.success() ? null : errorDetail(call.errorType(), call.errorMessage()),
        bound(call.resultPreview(), PROVIDER_PREVIEW_LIMIT),
        durationLabel(call.durationMillis()),
        call.sideEffecting());
  }

  private static ProviderEntry toProviderEntry(
      BootstrapVerificationQueries.BootstrapToolVerification provider) {
    return new ProviderEntry(
        provider.displayName(),
        provider.providerId(),
        provider.toolset(),
        provider.available() ? "Available" : "Missing",
        provider.available() ? "is-success is-light" : "is-warning is-light",
        bound(provider.missingReason(), PROVIDER_PREVIEW_LIMIT));
  }

  private static String[] evidence(String conversationId) {
    if (conversationId == null || conversationId.isBlank()) {
      return new String[] {null, null};
    }
    try {
      ActorTaskExecutionReference reference = ActorTaskExecutionReference.parse(conversationId);
      return new String[] {"View job evidence", "/jobs/" + reference.taskReference().value()};
    } catch (IllegalArgumentException ignored) {
      // Not an actor task conversation; fall through to the other evidence shapes.
    }
    if (UUID.matcher(conversationId).matches()) {
      return new String[] {"View job evidence", "/jobs/" + conversationId};
    }
    return new String[] {"View in chat", "/chat"};
  }

  private static String errorDetail(String errorType, String errorMessage) {
    if (errorType == null || errorType.isBlank()) {
      return bound(errorMessage, PROVIDER_PREVIEW_LIMIT);
    }
    String message = errorMessage == null || errorMessage.isBlank() ? "" : ": " + errorMessage;
    return bound(errorType + message, PROVIDER_PREVIEW_LIMIT);
  }

  private static String bound(String value, int limit) {
    if (value == null) {
      return null;
    }
    return value.length() <= limit ? value : value.substring(0, limit) + "...";
  }

  private static String durationLabel(long durationMillis) {
    if (durationMillis < 1_000) {
      return durationMillis + " ms";
    }
    if (durationMillis < 60_000) {
      return String.format(Locale.ENGLISH, "%.1f s", durationMillis / 1_000.0);
    }
    return String.format(Locale.ENGLISH, "%.1f min", durationMillis / 60_000.0);
  }

  private static boolean isLive(Task.Status status) {
    return status == Task.Status.todo
        || status == Task.Status.in_progress
        || status == Task.Status.awaiting_human_input;
  }

  private static String statusLabel(Task.Status status) {
    return switch (status) {
      case todo -> "Queued";
      case in_progress -> "Running";
      case completed -> "Completed";
      case cancelled -> "Cancelled";
      case awaiting_human_input -> "Needs attention";
      case failed -> "Failed";
    };
  }

  private static String statusClass(Task.Status status) {
    return switch (status) {
      case todo -> "is-light";
      case in_progress -> "is-info is-light";
      case completed -> "is-success is-light";
      case cancelled -> "is-light";
      case awaiting_human_input -> "is-warning is-light";
      case failed -> "is-danger is-light";
    };
  }

  private static String statusDetail(Task.Status status) {
    return switch (status) {
      case in_progress -> "SEA is working on this job now.";
      case awaiting_human_input -> "SEA needs input before this job can continue.";
      case todo -> "Waiting to start.";
      case completed, cancelled, failed -> "";
    };
  }

  public record MonitoringModel(
      String refreshedAt,
      Snapshot snapshot,
      List<JobEntry> liveJobs,
      List<JobEntry> terminalJobs,
      List<RunEntry> runs,
      List<CallEntry> calls,
      List<ProviderEntry> providers,
      boolean viewerConfigured,
      String viewerUrl) {}

  public record Snapshot(
      int liveJobs, int recordedRuns, int failedRuns, int providerCalls, int failedProviderCalls) {}

  public record JobEntry(
      String name, String statusLabel, String statusClass, String detail, String url) {}

  public record RunEntry(
      String id,
      String promptTypeLabel,
      String statusLabel,
      String statusClass,
      boolean failed,
      String startedAt,
      String durationLabel,
      int contextCharactersUsed,
      int contextCharacterBudget,
      String promptPreview,
      String resultPreview,
      String errorPreview,
      String evidenceLabel,
      String evidenceUrl) {}

  public record CallEntry(
      String timestamp,
      String providerId,
      String toolName,
      String outcomeLabel,
      String outcomeClass,
      boolean failed,
      String errorDetail,
      String resultPreview,
      String durationLabel,
      boolean sideEffecting) {}

  public record ProviderEntry(
      String displayName,
      String providerId,
      String toolset,
      String statusLabel,
      String statusClass,
      String missingReason) {}
}
