package org.zalava.web.ui;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.zalava.capabilities.approval.SeaToolApprovalRequests;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperations;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.identity.accounts.security.AuthenticatedActorResolver;
import org.zalava.tasks.application.port.in.ActorTaskCommands;
import org.zalava.tasks.application.port.in.TaskCommands;
import org.zalava.tasks.application.port.in.TaskQueries;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;
import org.zalava.tasks.domain.TaskNotFoundException;
import org.zalava.tasks.domain.TaskReference;

@Controller
public class JobDetailController {

  private static final DateTimeFormatter CREATED_AT =
      DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm", Locale.ENGLISH);

  private final TaskQueries taskQueries;
  private final TaskCommands taskCommands;
  private final SeaToolApprovalRequests approvalRequests;
  private final ActorTaskCommands actorTasks;
  private final AuthenticatedActorResolver actors;
  private final ProviderToolOperations providerOperations;

  public JobDetailController(
      TaskQueries taskQueries,
      TaskCommands taskCommands,
      SeaToolApprovalRequests approvalRequests,
      ActorTaskCommands actorTasks,
      AuthenticatedActorResolver actors,
      ProviderToolOperations providerOperations) {
    this.taskQueries = taskQueries;
    this.taskCommands = taskCommands;
    this.approvalRequests = approvalRequests;
    this.actorTasks = actorTasks;
    this.actors = actors;
    this.providerOperations = providerOperations;
  }

  @GetMapping("/jobs/{reference:[0-9a-fA-F-]{36}}")
  public String actorJobDetail(
      @PathVariable String reference, Authentication authentication, Model model, CsrfToken csrf) {
    Actor actor = actors.actor(authentication);
    ActorTaskReference taskReference = actorReference(reference);
    try {
      Task task = actorTasks.get(actor, taskReference);
      model.addAttribute(
          "model",
          toModel(task, taskReference.value(), approvalRequests.entriesFor(actor, taskReference)));
      model.addAttribute("csrf", csrf);
      return "ui/job-detail";
    } catch (TaskNotFoundException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found", exception);
    }
  }

  @GetMapping("/jobs/{date}/{filename:.+}")
  public String jobDetail(
      @PathVariable String date,
      @PathVariable String filename,
      Authentication authentication,
      Model model,
      CsrfToken csrf) {
    requireLegacyAccess(authentication);
    TaskReference reference;
    try {
      reference = TaskReference.parse(date, filename);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
    }
    try {
      Task task = taskQueries.getTask(reference);
      model.addAttribute(
          "model", toModel(task, reference.path(), approvalRequests.pendingFor(reference)));
      model.addAttribute("csrf", csrf);
      return "ui/job-detail";
    } catch (TaskNotFoundException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
    }
  }

  @PostMapping("/jobs/{reference:[0-9a-fA-F-]{36}}/approvals/{requestId}/allow")
  public String allowActorApproval(
      @PathVariable String reference,
      @PathVariable String requestId,
      Authentication authentication,
      RedirectAttributes redirectAttributes) {
    return decideActor(
        reference, requestId, ApprovalDecision.ALLOW_ONCE, authentication, redirectAttributes);
  }

  @PostMapping("/jobs/{reference:[0-9a-fA-F-]{36}}/approvals/{requestId}/allow-tool")
  public String allowActorToolApproval(
      @PathVariable String reference,
      @PathVariable String requestId,
      Authentication authentication,
      RedirectAttributes redirectAttributes) {
    return decideActor(
        reference, requestId, ApprovalDecision.ALLOW_TOOL, authentication, redirectAttributes);
  }

  @PostMapping("/jobs/{reference:[0-9a-fA-F-]{36}}/approvals/{requestId}/deny")
  public String denyActorApproval(
      @PathVariable String reference,
      @PathVariable String requestId,
      Authentication authentication,
      RedirectAttributes redirectAttributes) {
    return decideActor(
        reference, requestId, ApprovalDecision.DENY, authentication, redirectAttributes);
  }

  @PostMapping("/jobs/{date}/{filename:.+}/approvals/{requestId}/allow")
  public String allowApproval(
      @PathVariable String date,
      @PathVariable String filename,
      @PathVariable String requestId,
      Authentication authentication,
      RedirectAttributes redirectAttributes) {
    return decide(
        date, filename, requestId, ApprovalDecision.ALLOW_ONCE, authentication, redirectAttributes);
  }

  @PostMapping("/jobs/{date}/{filename:.+}/approvals/{requestId}/allow-tool")
  public String allowToolApproval(
      @PathVariable String date,
      @PathVariable String filename,
      @PathVariable String requestId,
      Authentication authentication,
      RedirectAttributes redirectAttributes) {
    return decide(
        date, filename, requestId, ApprovalDecision.ALLOW_TOOL, authentication, redirectAttributes);
  }

  @PostMapping("/jobs/{date}/{filename:.+}/approvals/{requestId}/deny")
  public String denyApproval(
      @PathVariable String date,
      @PathVariable String filename,
      @PathVariable String requestId,
      Authentication authentication,
      RedirectAttributes redirectAttributes) {
    return decide(
        date, filename, requestId, ApprovalDecision.DENY, authentication, redirectAttributes);
  }

  private String decide(
      String date,
      String filename,
      String requestId,
      ApprovalDecision decision,
      Authentication authentication,
      RedirectAttributes redirectAttributes) {
    requireLegacyAccess(authentication);
    TaskReference reference = parseReference(date, filename);
    try {
      Task task = taskQueries.getTask(reference);
      if (task.getStatus() != Task.Status.awaiting_human_input) {
        throw new IllegalStateException("Job is not waiting for an approval decision");
      }
      switch (decision) {
        case ALLOW_ONCE -> approvalRequests.allow(requestId, reference);
        case ALLOW_TOOL -> approvalRequests.allowTool(requestId, reference);
        case DENY -> approvalRequests.deny(requestId, reference);
      }
      if (!approvalRequests.hasPending(reference)) {
        taskCommands.resume(reference);
      }
      redirectAttributes.addFlashAttribute("approvalMessage", decision.message());
      return "redirect:/jobs/" + reference.path();
    } catch (SeaToolApprovalRequests.NotFoundException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval request not found", ex);
    } catch (TaskNotFoundException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found", ex);
    } catch (SeaToolApprovalRequests.AlreadyDecidedException | IllegalStateException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
    }
  }

  private String decideActor(
      String reference,
      String requestId,
      ApprovalDecision decision,
      Authentication authentication,
      RedirectAttributes redirectAttributes) {
    Actor actor = actors.actor(authentication);
    ActorTaskReference taskReference = actorReference(reference);
    try {
      Task task = actorTasks.get(actor, taskReference);
      if (task.getStatus() != Task.Status.awaiting_human_input) {
        throw new IllegalStateException("Job is not waiting for an approval decision");
      }
      SeaToolApprovalRequests.Entry approval =
          approvalRequests.get(actor, taskReference, requestId);
      if (actors.role(authentication) == AccountRole.MEMBER
          && !"MEMBER".equals(approval.attributes().get("accountRole"))) {
        throw new SeaToolApprovalRequests.NotFoundException(requestId);
      }
      switch (decision) {
        case ALLOW_ONCE -> providerOperations.allowUnscoped(requestId);
        case ALLOW_TOOL -> providerOperations.allowUnscopedTool(requestId);
        case DENY -> providerOperations.denyUnscoped(requestId);
      }
      if (!approvalRequests.hasPending(actor, taskReference)) {
        actorTasks.resume(actor, taskReference);
      }
      redirectAttributes.addFlashAttribute("approvalMessage", decision.message());
      return "redirect:/jobs/" + taskReference.value();
    } catch (SeaToolApprovalRequests.NotFoundException exception) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Approval request not found", exception);
    } catch (TaskNotFoundException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found", exception);
    } catch (SeaToolApprovalRequests.AlreadyDecidedException | IllegalStateException exception) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
    }
  }

  private static TaskReference parseReference(String date, String filename) {
    try {
      return TaskReference.parse(date, filename);
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found");
    }
  }

  private static ActorTaskReference actorReference(String value) {
    try {
      return new ActorTaskReference(value);
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found", exception);
    }
  }

  private void requireLegacyAccess(Authentication authentication) {
    if (authentication == null || authentication instanceof AnonymousAuthenticationToken) return;
    if (authentication.isAuthenticated() && actors.role(authentication) != AccountRole.ADMIN) {
      throw new AccessDeniedException("Legacy jobs require an administrator");
    }
  }

  private static JobDetailModel toModel(
      Task task, String referencePath, List<SeaToolApprovalRequests.Entry> approvals) {
    return new JobDetailModel(
        task.getName(),
        task.getGoalDescription(),
        task.getAgentFeedback().orElse(null),
        task.getStatus() == Task.Status.failed ? task.getFailureDetail().orElse(null) : null,
        CREATED_AT.format(task.getCreatedAt().atZone(ZoneId.systemDefault())),
        statusLabel(task.getStatus()),
        statusClass(task.getStatus()),
        statusGuidance(task.getStatus()),
        currentStepLabel(task.getStatus()),
        statusGuidance(task.getStatus()),
        lifecycle(task),
        planEntries(task),
        artifactEntries(task),
        logEntries(task),
        referencePath,
        approvals.stream().map(JobDetailController::toApprovalEntry).toList());
  }

  private static List<LifecycleEntry> lifecycle(Task task) {
    return List.of(
        new LifecycleEntry(
            "Created",
            CREATED_AT.format(task.getCreatedAt().atZone(ZoneId.systemDefault())),
            "Job recorded in the workspace."),
        new LifecycleEntry(statusLabel(task.getStatus()), null, statusGuidance(task.getStatus())));
  }

  private static ApprovalEntry toApprovalEntry(SeaToolApprovalRequests.Entry request) {
    return new ApprovalEntry(
        request.requestId(),
        request.providerId(),
        request.toolName(),
        request.argumentsJson(),
        request.policyTags(),
        request.decision() == SeaToolApprovalRequests.Decision.PENDING
            ? "Pending approval"
            : request.decision() == SeaToolApprovalRequests.Decision.DENIED ? "Denied" : "Allowed",
        request.decision() == SeaToolApprovalRequests.Decision.PENDING);
  }

  private static List<SectionEntry> planEntries(Task task) {
    return textEntry("Goal", task.getGoalDescription());
  }

  private static List<SectionEntry> artifactEntries(Task task) {
    if (task.getStatus() == Task.Status.failed) {
      return textEntry("Failure detail", task.getFailureDetail().orElse(null));
    }
    return textEntry("Execution result", task.getAgentFeedback().orElse(null));
  }

  private static List<SectionEntry> logEntries(Task task) {
    return lifecycle(task).stream()
        .map(
            entry ->
                new SectionEntry(
                    entry.label(),
                    entry.occurredAt() == null
                        ? entry.detail()
                        : entry.occurredAt() + " - " + entry.detail()))
        .toList();
  }

  private static List<SectionEntry> textEntry(String label, String text) {
    if (text == null || text.isBlank()) {
      return List.of();
    }
    return List.of(new SectionEntry(label, text));
  }

  private static String statusLabel(Task.Status status) {
    return switch (status) {
      case todo -> "Queued";
      case in_progress -> "Running";
      case awaiting_human_input -> "Waiting for Input";
      case completed -> "Completed";
      case cancelled -> "Cancelled";
      case failed -> "Failed";
    };
  }

  private static String statusClass(Task.Status status) {
    return switch (status) {
      case todo -> "is-light";
      case in_progress -> "is-info is-light";
      case awaiting_human_input -> "is-warning is-light";
      case completed -> "is-success is-light";
      case cancelled -> "is-light";
      case failed -> "is-danger is-light";
    };
  }

  private static String currentStepLabel(Task.Status status) {
    return switch (status) {
      case todo -> "Waiting to start";
      case in_progress -> "Working";
      case awaiting_human_input -> "Waiting for input";
      case completed -> "Finished";
      case cancelled -> "Cancelled";
      case failed -> "Stopped after failure";
    };
  }

  private static String statusGuidance(Task.Status status) {
    return switch (status) {
      case todo -> "SEA has recorded this job and it is waiting to run.";
      case in_progress -> "SEA is currently working on this job.";
      case awaiting_human_input -> "SEA needs human input before this job can continue.";
      case completed -> "SEA has completed this job.";
      case cancelled -> "SEA cancelled this job.";
      case failed -> "SEA could not complete this job after retrying it.";
    };
  }

  public record JobDetailModel(
      String name,
      String goalDescription,
      String agentFeedback,
      String failureDetail,
      String createdAt,
      String statusLabel,
      String statusClass,
      String statusGuidance,
      String currentStepLabel,
      String currentStepDetail,
      List<LifecycleEntry> lifecycle,
      List<SectionEntry> planEntries,
      List<SectionEntry> artifactEntries,
      List<SectionEntry> logEntries,
      String referencePath,
      List<ApprovalEntry> approvals) {}

  public record LifecycleEntry(String label, String occurredAt, String detail) {}

  public record SectionEntry(String label, String detail) {}

  public record ApprovalEntry(
      String requestId,
      String providerId,
      String toolName,
      String argumentsJson,
      List<String> policyTags,
      String decision,
      boolean pending) {}

  private enum ApprovalDecision {
    ALLOW_ONCE("Approval granted. The job has been queued to continue."),
    ALLOW_TOOL("Tool approval saved. The job has been queued to continue."),
    DENY("Approval denied. The job has been queued to continue.");

    private final String message;

    ApprovalDecision(String message) {
      this.message = message;
    }

    String message() {
      return message;
    }
  }
}
