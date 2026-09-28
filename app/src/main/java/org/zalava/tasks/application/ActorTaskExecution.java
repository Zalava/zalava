package org.zalava.tasks.application;

import java.util.stream.Collectors;
import org.zalava.tasks.application.port.out.ActorTaskAgent;
import org.zalava.tasks.application.port.out.ActorTaskApprovalDecisions;
import org.zalava.tasks.application.port.out.ActorTaskClarifications;
import org.zalava.tasks.application.port.out.ActorTaskNotifier;
import org.zalava.tasks.application.port.out.ActorTaskStore;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.Task;

/** Trusted scheduler entry point for owner-scoped task execution. */
public final class ActorTaskExecution {
  private final ActorTaskStore tasks;
  private final ActorTaskAgent agent;
  private final ActorTaskNotifier notifier;
  private final ActorTaskApprovalDecisions approvals;
  private final ActorTaskClarifications clarifications;

  public ActorTaskExecution(
      ActorTaskStore tasks, ActorTaskAgent agent, ActorTaskNotifier notifier) {
    this(tasks, agent, notifier, null, null);
  }

  public ActorTaskExecution(
      ActorTaskStore tasks,
      ActorTaskAgent agent,
      ActorTaskNotifier notifier,
      ActorTaskApprovalDecisions approvals) {
    this(tasks, agent, notifier, approvals, null);
  }

  public ActorTaskExecution(
      ActorTaskStore tasks,
      ActorTaskAgent agent,
      ActorTaskNotifier notifier,
      ActorTaskApprovalDecisions approvals,
      ActorTaskClarifications clarifications) {
    this.tasks = tasks;
    this.agent = agent;
    this.notifier = notifier;
    this.approvals = approvals;
    this.clarifications = clarifications;
  }

  public Task.Status execute(String schedulerToken) {
    ActorTaskExecutionReference execution = ActorTaskExecutionReference.parse(schedulerToken);
    Task task = tasks.get(execution.actor(), execution.taskReference());
    if (task.getStatus() != Task.Status.todo) {
      throw new IllegalStateException("Only queued actor tasks can execute");
    }
    Task inProgress =
        tasks.save(
            execution.actor(), execution.taskReference(), task.withStatus(Task.Status.in_progress));
    try {
      ActorTaskAgent.Result result =
          agent.execute(
              execution.actor(), execution.taskReference(), prompt(execution, inProgress));
      if (clarifications != null
          && clarifications.hasPending(execution.actor(), execution.taskReference())) {
        result =
            new ActorTaskAgent.Result(
                Task.Status.awaiting_human_input, clarificationFeedback(execution));
      } else if (approvals != null
          && approvals.hasPending(execution.actor(), execution.taskReference())) {
        result =
            new ActorTaskAgent.Result(Task.Status.awaiting_human_input, pendingFeedback(execution));
      }
      Task finished =
          tasks.save(
              execution.actor(),
              execution.taskReference(),
              inProgress.withFeedback(result.feedback()).withStatus(result.newStatus()));
      notifier.notify(
          execution.actor(), finished.getName(), finished.getStatus(), result.feedback());
      return finished.getStatus();
    } catch (RuntimeException exception) {
      tasks.save(
          execution.actor(), execution.taskReference(), inProgress.withStatus(Task.Status.todo));
      throw exception;
    }
  }

  private String prompt(ActorTaskExecutionReference execution, Task task) {
    String decisions =
        approvals == null
            ? ""
            : approvals.unconsumedFor(execution.actor(), execution.taskReference()).stream()
                .map(
                    decision ->
                        "- %s %s/%s with arguments %s"
                            .formatted(
                                decision.decision(),
                                decision.providerId(),
                                decision.toolName(),
                                decision.argumentsJson()))
                .collect(Collectors.joining(System.lineSeparator()));
    String decisionContext =
        decisions.isBlank()
            ? ""
            : "\n\nApproval decisions since the previous attempt:\n"
                + decisions
                + "\nRetry an allowed tool call with the same arguments."
                + " Do not execute a denied operation.";
    String clarificationContext = clarificationContext(execution);
    return "Handle task '%s': %s%s%s"
        .formatted(
            task.getName(), task.getGoalDescription(), decisionContext, clarificationContext);
  }

  private String clarificationContext(ActorTaskExecutionReference execution) {
    if (clarifications == null) return "";
    String resolved =
        clarifications.resolvedFor(execution.actor(), execution.taskReference()).stream()
            .map(
                clarification ->
                    "- [%s] %s: %s"
                        .formatted(
                            clarification.status(),
                            clarification.requestId(),
                            clarification.summary()))
            .collect(Collectors.joining(System.lineSeparator()));
    if (resolved.isBlank()) return "";
    return "\n\nClarification answers since the previous attempt:\n"
        + resolved
        + "\nUse the owner's answers and do not ask again for the same information.";
  }

  private String clarificationFeedback(ActorTaskExecutionReference execution) {
    var pending = clarifications.pendingFor(execution.actor(), execution.taskReference());
    return pending.stream()
        .map(
            clarification ->
                "Clarification request %s: %s (choices: %s)"
                    .formatted(
                        clarification.requestId(),
                        clarification.prompt(),
                        clarification.choices().isEmpty()
                            ? "free text"
                            : String.join(", ", clarification.choices())))
        .collect(Collectors.joining(System.lineSeparator()));
  }

  private String pendingFeedback(ActorTaskExecutionReference execution) {
    var pending = approvals.pendingFor(execution.actor(), execution.taskReference());
    if (pending.isEmpty()) return "Waiting for approval of a side-effecting SEA tool call.";
    return pending.stream()
        .map(
            approval ->
                "Request %s: %s (%s/%s, %s)"
                    .formatted(
                        approval.requestId(),
                        approval.prompt(),
                        approval.providerId(),
                        approval.toolName(),
                        approval.argumentsPreview()))
        .collect(Collectors.joining(System.lineSeparator()));
  }
}
