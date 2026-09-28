package org.zalava.tasks.application;

import java.util.List;
import java.util.stream.Collectors;
import org.zalava.observability.application.port.out.OperationalMetrics;
import org.zalava.tasks.application.port.in.TaskExecution;
import org.zalava.tasks.application.port.out.TaskAgent;
import org.zalava.tasks.application.port.out.TaskApprovalDecisions;
import org.zalava.tasks.application.port.out.TaskNotifier;
import org.zalava.tasks.application.port.out.TaskStore;
import org.zalava.tasks.domain.Task;
import org.zalava.tasks.domain.TaskReference;

public class DefaultTaskExecution implements TaskExecution {

  private static final String TERMINAL_FAILURE_DETAIL =
      "SEA could not complete this job after multiple attempts.";

  private final TaskStore taskStore;
  private final TaskAgent taskAgent;
  private final TaskApprovalDecisions approvalDecisions;
  private final TaskNotifier taskNotifier;
  private final OperationalMetrics metrics;

  public DefaultTaskExecution(
      TaskStore taskStore,
      TaskAgent taskAgent,
      TaskApprovalDecisions approvalDecisions,
      TaskNotifier taskNotifier) {
    this(taskStore, taskAgent, approvalDecisions, taskNotifier, OperationalMetrics.NOOP);
  }

  public DefaultTaskExecution(
      TaskStore taskStore,
      TaskAgent taskAgent,
      TaskApprovalDecisions approvalDecisions,
      TaskNotifier taskNotifier,
      OperationalMetrics metrics) {
    this.taskStore = taskStore;
    this.taskAgent = taskAgent;
    this.approvalDecisions = approvalDecisions;
    this.taskNotifier = taskNotifier;
    this.metrics = metrics;
  }

  @Override
  public ExecutionOutcome execute(String taskId, FailureHandling failureHandling) {
    long startedAt = System.nanoTime();
    Task task = taskStore.getTaskById(taskId);
    requireQueued(task);

    Task inProgress = taskStore.save(task.withStatus(Task.Status.in_progress));
    TaskReference taskReference = taskStore.getReference(inProgress);
    try {
      TaskAgent.Result result =
          taskAgent.execute(taskId, taskReference, formatTaskForAgent(inProgress, taskReference));
      if (approvalDecisions.hasPending(taskReference)) {
        result =
            new TaskAgent.Result(
                Task.Status.awaiting_human_input, formatPendingApprovals(taskReference));
      }
      Task finished =
          taskStore.save(inProgress.withFeedback(result.feedback()).withStatus(result.newStatus()));
      taskNotifier.notify(task.getName(), result.newStatus(), result.feedback());
      observe("succeeded", finished.getStatus(), startedAt);
      return new ExecutionOutcome(finished.getName(), finished.getStatus());
    } catch (Exception exception) {
      Task failed = taskStore.save(failedAttempt(inProgress, failureHandling));
      observe("failed", failed.getStatus(), startedAt);
      throw exception;
    }
  }

  private void observe(String outcome, Task.Status state, long startedAt) {
    try {
      metrics.taskExecution(outcome, state.name(), (System.nanoTime() - startedAt) / 1_000_000);
    } catch (RuntimeException ignored) {
      // Metrics must not affect task execution.
    }
  }

  private void requireQueued(Task task) {
    if (!Task.Status.todo.equals(task.getStatus())) {
      throw new IllegalStateException(
          "Cannot handle task '"
              + task.getName()
              + "' with status "
              + task.getStatus()
              + ". Only tasks that have status todo can be run");
    }
  }

  private Task failedAttempt(Task inProgress, FailureHandling failureHandling) {
    if (failureHandling == FailureHandling.TERMINAL) {
      return inProgress.withStatus(Task.Status.failed).withFailureDetail(TERMINAL_FAILURE_DETAIL);
    }
    return inProgress.withStatus(Task.Status.todo);
  }

  private String formatPendingApprovals(TaskReference taskReference) {
    List<TaskApprovalDecisions.PendingApproval> approvals =
        approvalDecisions.pendingFor(taskReference);
    if (approvals.isEmpty()) {
      return "Waiting for approval of a side-effecting SEA tool call.";
    }
    String approvalPrompts =
        approvals.stream()
            .map(
                approval ->
                    """
                        Request %s
                        %s
                        Provider/tool: %s/%s
                        Effect: %s
                        Arguments: %s
                        Scope: %s
                        Policy tags: %s
                        Allow once: %s
                        Always allow tool: %s
                        Deny: %s
                        Review job controls: /jobs/%s
                        """
                        .formatted(
                            approval.requestId(),
                            approval.prompt(),
                            approval.providerId(),
                            approval.toolName(),
                            approval.effect(),
                            approval.argumentsPreview(),
                            approval.scope().isEmpty() ? "none" : approval.scope(),
                            approval.policyTags().isEmpty()
                                ? "none"
                                : String.join(", ", approval.policyTags()),
                            approval.allowOnceCommand(),
                            approval.allowToolCommand(),
                            approval.denyCommand(),
                            taskReference.path())
                        .strip())
            .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
    return """
                SEA is waiting for your approval before continuing this job.

                %s
                """
        .formatted(approvalPrompts)
        .strip();
  }

  private String formatTaskForAgent(Task task, TaskReference taskReference) {
    String decisions =
        approvalDecisions.unconsumedFor(taskReference).stream()
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
            : """

                Approval decisions since the previous attempt:
                %s
                Retry an allowed tool call with the same arguments so SEA can consume the approval.
                Do not execute a denied operation; adapt the task result accordingly.
                """
                .formatted(decisions);
    return """
                Handle the following task and report the new status ('completed' or 'awaiting_human_input') with the feedback what was done
                Task '%s': %s%s
                """
        .formatted(task.getName(), task.getDescription(), decisionContext);
  }
}
