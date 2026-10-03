package org.zalava.web.ui;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.zalava.capabilities.approval.SeaToolApprovalRequests;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.identity.accounts.security.AuthenticatedActorResolver;
import org.zalava.tasks.application.port.in.ActorTaskCommands;
import org.zalava.tasks.application.port.in.TaskQueries;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.Task;

@Controller
public class DashboardController {

  private static final int RECENT_ACTIVITY_LIMIT = 8;
  private static final int CURRENT_WORK_LIMIT = 5;

  private static final DateTimeFormatter ACTIVITY_TIME = DateTimeFormatter.ofPattern("HH:mm");

  private final TaskQueries taskQueries;
  private final SeaToolApprovalRequests approvalRequests;
  private final ActorTaskCommands actorTasks;
  private final AuthenticatedActorResolver actors;
  private final org.zalava.tasks.application.port.in.ActorJobEvidenceQueries evidenceQueries;

  public DashboardController(
      TaskQueries taskQueries,
      SeaToolApprovalRequests approvalRequests,
      ActorTaskCommands actorTasks,
      AuthenticatedActorResolver actors,
      org.zalava.tasks.application.port.in.ActorJobEvidenceQueries evidenceQueries) {
    this.taskQueries = taskQueries;
    this.approvalRequests = approvalRequests;
    this.actorTasks = actorTasks;
    this.actors = actors;
    this.evidenceQueries = evidenceQueries;
  }

  @GetMapping("/dashboard")
  public String dashboard(Model model, Authentication authentication) {
    model.addAttribute(
        "model",
        actors
            .actorIfAuthenticated(authentication)
            .map(this::buildActorModel)
            .orElseGet(this::buildModel));
    model.addAttribute(
        "evidence",
        actors
            .actorIfAuthenticated(authentication)
            .map(evidenceQueries::snapshot)
            .orElseGet(
                org.zalava.tasks.application.port.in.ActorJobEvidenceQueries.Snapshot::empty));
    return "ui/dashboard";
  }

  private DashboardModel buildActorModel(Actor actor) {
    List<Task> tasks =
        actorTasks.list(actor).stream().map(reference -> actorTasks.get(actor, reference)).toList();
    return buildModel(tasks, pendingApprovals(actor), true);
  }

  private DashboardModel buildModel() {
    List<Task> tasks = taskQueries.getTasks(LocalDate.now(), null);
    return buildModel(tasks, pendingApprovals(), false);
  }

  private DashboardModel buildModel(
      List<Task> tasks, List<ApprovalEntry> pendingApprovals, boolean actorScoped) {
    int runningJobs = count(tasks, Task.Status.in_progress);
    int completedToday = count(tasks, Task.Status.completed);
    List<ActivityEntry> recentActivity =
        tasks.stream()
            .sorted(Comparator.comparing(Task::getCreatedAt).reversed())
            .limit(RECENT_ACTIVITY_LIMIT)
            .map(DashboardController::toActivityEntry)
            .toList();
    List<CurrentWorkEntry> currentWork =
        tasks.stream()
            .filter(DashboardController::isCurrentWork)
            .sorted(Comparator.comparing(Task::getCreatedAt).reversed())
            .limit(CURRENT_WORK_LIMIT)
            .map(task -> toCurrentWorkEntry(task, actorScoped))
            .toList();

    return new DashboardModel(
        runningJobs,
        pendingApprovals.size(),
        completedToday,
        pendingApprovals,
        currentWork,
        recentActivity);
  }

  private List<ApprovalEntry> pendingApprovals(Actor actor) {
    return approvalRequests.recentActorTaskEntries(actor).stream()
        .filter(entry -> entry.decision() == SeaToolApprovalRequests.Decision.PENDING)
        .map(
            entry -> {
              var reference =
                  ActorTaskExecutionReference.parse(
                          entry.attributes().get(SeaToolApprovalRequests.ACTOR_TASK_REFERENCE))
                      .taskReference();
              return new ApprovalEntry(
                  entry.toolName(), entry.providerId(), "/jobs/" + reference.value());
            })
        .toList();
  }

  private List<ApprovalEntry> pendingApprovals() {
    List<ApprovalEntry> approvals = new ArrayList<>();
    approvalRequests.recentEntries().stream()
        .filter(entry -> entry.decision() == SeaToolApprovalRequests.Decision.PENDING)
        .filter(entry -> entry.taskReference() != null)
        .forEach(
            entry ->
                approvals.add(
                    new ApprovalEntry(
                        entry.toolName(), entry.providerId(), "/jobs/" + entry.taskReference())));
    return List.copyOf(approvals);
  }

  private static int count(List<Task> tasks, Task.Status status) {
    return (int) tasks.stream().filter(task -> task.getStatus() == status).count();
  }

  private static ActivityEntry toActivityEntry(Task task) {
    return new ActivityEntry(
        task.getName(),
        statusLabel(task.getStatus()),
        statusClass(task.getStatus()),
        ACTIVITY_TIME.format(task.getCreatedAt().atZone(ZoneId.systemDefault())));
  }

  private static CurrentWorkEntry toCurrentWorkEntry(Task task, boolean actorScoped) {
    return new CurrentWorkEntry(
        task.getName(),
        statusLabel(task.getStatus()),
        statusClass(task.getStatus()),
        currentWorkDetail(task.getStatus()),
        actorScoped ? "/jobs/" + task.getId() : jobUrl(task));
  }

  private static String jobUrl(Task task) {
    Path path = Path.of(task.getId());
    return "/jobs/" + path.getParent().getFileName() + "/" + path.getFileName();
  }

  private static boolean isCurrentWork(Task task) {
    return task.getStatus() == Task.Status.in_progress
        || task.getStatus() == Task.Status.awaiting_human_input;
  }

  private static String currentWorkDetail(Task.Status status) {
    return switch (status) {
      case in_progress -> "SEA is working on this job now.";
      case awaiting_human_input -> "SEA needs your input before this job can continue.";
      case todo, completed, cancelled, failed -> "";
    };
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

  public record DashboardModel(
      int runningJobs,
      int pendingApprovals,
      int completedToday,
      List<ApprovalEntry> approvals,
      List<CurrentWorkEntry> currentWork,
      List<ActivityEntry> recentActivity) {}

  public record CurrentWorkEntry(
      String name, String statusLabel, String statusClass, String detail, String url) {}

  public record ActivityEntry(
      String name, String statusLabel, String statusClass, String createdAt) {}

  public record ApprovalEntry(String toolName, String providerId, String jobUrl) {}
}
