package org.zalava.tasks.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.FileSystemResource;
import org.zalava.tasks.adapter.out.filesystem.FileSystemTaskRepository;
import org.zalava.tasks.application.port.in.TaskExecution;
import org.zalava.tasks.application.port.out.TaskAgent;
import org.zalava.tasks.application.port.out.TaskApprovalDecisions;
import org.zalava.tasks.application.port.out.TaskNotifier;
import org.zalava.tasks.application.port.out.TaskStore;
import org.zalava.tasks.domain.Task;
import org.zalava.tasks.domain.TaskReference;

class DefaultTaskExecutionTest {

  @Test
  void marksTaskAwaitingHumanInputWhenExecutionCreatesPendingApproval() {
    TaskStore taskStore = mock(TaskStore.class);
    TaskAgent taskAgent = mock(TaskAgent.class);
    TaskApprovalDecisions approvals = mock(TaskApprovalDecisions.class);
    TaskNotifier notifier = mock(TaskNotifier.class);
    DefaultTaskExecution execution =
        new DefaultTaskExecution(taskStore, taskAgent, approvals, notifier);
    TaskReference reference = TaskReference.parse("2026-06-08", "120000-write-file.md");
    Task task = task(reference, Task.Status.todo, "Write a note.");

    when(taskStore.getTaskById(task.getId())).thenReturn(task);
    when(taskStore.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(taskStore.getReference(any(Task.class))).thenReturn(reference);
    when(taskAgent.execute(eq(task.getId()), eq(reference), any(String.class)))
        .thenReturn(new TaskAgent.Result(Task.Status.completed, "Done"));
    when(approvals.hasPending(reference)).thenReturn(true);

    TaskExecution.ExecutionOutcome outcome =
        execution.execute(task.getId(), TaskExecution.FailureHandling.RETRYABLE);

    ArgumentCaptor<Task> savedTasks = ArgumentCaptor.forClass(Task.class);
    verify(taskStore, org.mockito.Mockito.times(2)).save(savedTasks.capture());
    Task finalTask = savedTasks.getAllValues().getLast();
    assertThat(finalTask.getStatus()).isEqualTo(Task.Status.awaiting_human_input);
    assertThat(finalTask.getDescription()).isEqualTo("Write a note.");
    assertThat(finalTask.getAgentFeedback())
        .hasValueSatisfying(feedback -> assertThat(feedback).contains("Waiting for approval"));
    assertThat(outcome.status()).isEqualTo(Task.Status.awaiting_human_input);
    verify(notifier)
        .notify(
            "Write file",
            Task.Status.awaiting_human_input,
            "Waiting for approval of a side-effecting Zalava tool call.");
  }

  @Test
  void includesPendingApprovalPromptWhenTaskAwaitsHumanInput() {
    TaskStore taskStore = mock(TaskStore.class);
    TaskAgent taskAgent = mock(TaskAgent.class);
    TaskApprovalDecisions approvals = mock(TaskApprovalDecisions.class);
    TaskNotifier notifier = mock(TaskNotifier.class);
    DefaultTaskExecution execution =
        new DefaultTaskExecution(taskStore, taskAgent, approvals, notifier);
    TaskReference reference = TaskReference.parse("2026-06-08", "120000-write-file.md");
    Task task = task(reference, Task.Status.todo, "Write a note.");

    when(taskStore.getTaskById(task.getId())).thenReturn(task);
    when(taskStore.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(taskStore.getReference(any(Task.class))).thenReturn(reference);
    when(taskAgent.execute(eq(task.getId()), eq(reference), any(String.class)))
        .thenReturn(new TaskAgent.Result(Task.Status.completed, "Done"));
    when(approvals.hasPending(reference)).thenReturn(true);
    when(approvals.pendingFor(reference))
        .thenReturn(
            List.of(
                new TaskApprovalDecisions.PendingApproval(
                    "approval-123",
                    "filesystem-workspace",
                    "writeFile",
                    "telegram-42",
                    "Zalava requests approval to run writeFile on provider filesystem-workspace for actor telegram-42.",
                    "side-effecting",
                    "{\"path\":\"notes/a.txt\"}",
                    "/zalava approve approval-123",
                    "/zalava always-allow-tool approval-123",
                    "/zalava deny approval-123",
                    Map.of("root", "workspace"),
                    List.of("filesystem.write"))));

    execution.execute(task.getId(), TaskExecution.FailureHandling.RETRYABLE);

    ArgumentCaptor<Task> savedTasks = ArgumentCaptor.forClass(Task.class);
    verify(taskStore, org.mockito.Mockito.times(2)).save(savedTasks.capture());
    Task finalTask = savedTasks.getAllValues().getLast();
    assertThat(finalTask.getAgentFeedback())
        .hasValueSatisfying(
            feedback ->
                assertThat(feedback)
                    .contains("Zalava is waiting for your approval before continuing this job.")
                    .contains("Request approval-123")
                    .contains(
                        "Zalava requests approval to run writeFile on provider filesystem-workspace")
                    .contains("Provider/tool: filesystem-workspace/writeFile")
                    .contains("Arguments: {\"path\":\"notes/a.txt\"}")
                    .contains("Scope: {root=workspace}")
                    .contains("Policy tags: filesystem.write")
                    .contains("Allow once: /zalava approve approval-123")
                    .contains("Always allow tool: /zalava always-allow-tool approval-123")
                    .contains("Deny: /zalava deny approval-123")
                    .contains("Review job controls: /jobs/2026-06-08/120000-write-file.md"));
    verify(notifier)
        .notify(
            eq("Write file"),
            eq(Task.Status.awaiting_human_input),
            org.mockito.ArgumentMatchers.contains("/zalava approve approval-123"));
  }

  @Test
  void includesApprovalDecisionWhenResumingTask() {
    TaskStore taskStore = mock(TaskStore.class);
    TaskAgent taskAgent = mock(TaskAgent.class);
    TaskApprovalDecisions approvals = mock(TaskApprovalDecisions.class);
    DefaultTaskExecution execution =
        new DefaultTaskExecution(taskStore, taskAgent, approvals, mock(TaskNotifier.class));
    TaskReference reference = TaskReference.parse("2026-06-08", "120000-write-file.md");
    Task task = task(reference, Task.Status.todo, "Write a note.");

    when(taskStore.getTaskById(task.getId())).thenReturn(task);
    when(taskStore.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(taskStore.getReference(any(Task.class))).thenReturn(reference);
    when(approvals.unconsumedFor(reference))
        .thenReturn(
            List.of(
                new TaskApprovalDecisions.Decision(
                    "ALLOWED", "filesystem-workspace", "write", "{\"path\":\"notes/a.txt\"}")));
    when(taskAgent.execute(eq(task.getId()), eq(reference), any(String.class)))
        .thenReturn(new TaskAgent.Result(Task.Status.completed, "Done"));

    execution.execute(task.getId(), TaskExecution.FailureHandling.RETRYABLE);

    ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
    verify(taskAgent).execute(eq(task.getId()), eq(reference), prompt.capture());
    assertThat(prompt.getValue())
        .contains("ALLOWED filesystem-workspace/write with arguments")
        .contains("Retry an allowed tool call with the same arguments");
  }

  @Test
  void returnsTaskToTodoWhenExecutionCanBeRetried() {
    TaskStore taskStore = mock(TaskStore.class);
    TaskAgent taskAgent = mock(TaskAgent.class);
    TaskNotifier notifier = mock(TaskNotifier.class);
    DefaultTaskExecution execution =
        new DefaultTaskExecution(taskStore, taskAgent, mock(TaskApprovalDecisions.class), notifier);
    TaskReference reference = TaskReference.parse("2026-06-08", "120000-fail.md");
    Task task = task(reference, Task.Status.todo, "Try work.");

    when(taskStore.getTaskById(task.getId())).thenReturn(task);
    when(taskStore.save(any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(taskStore.getReference(any(Task.class))).thenReturn(reference);
    when(taskAgent.execute(eq(task.getId()), eq(reference), any(String.class)))
        .thenThrow(new IllegalStateException("Temporary upstream failure"));

    assertThatThrownBy(
            () -> execution.execute(task.getId(), TaskExecution.FailureHandling.RETRYABLE))
        .isInstanceOf(IllegalStateException.class);

    ArgumentCaptor<Task> savedTasks = ArgumentCaptor.forClass(Task.class);
    verify(taskStore, org.mockito.Mockito.times(2)).save(savedTasks.capture());
    Task retryable = savedTasks.getAllValues().getLast();
    assertThat(retryable.getStatus()).isEqualTo(Task.Status.todo);
    assertThat(retryable.getFailureDetail()).isEmpty();
    verify(notifier, never()).notify(any(String.class), any(Task.Status.class), any(String.class));
  }

  @Test
  void persistsGenericTerminalFailureWithoutRawExceptionDetail(@TempDir Path workspaceDir)
      throws Exception {
    FileSystemTaskRepository taskStore =
        new FileSystemTaskRepository(new FileSystemResource(workspaceDir));
    Task task = taskStore.save(Task.newTask("Fail", "Try work."));
    TaskAgent taskAgent = mock(TaskAgent.class);
    DefaultTaskExecution execution =
        new DefaultTaskExecution(
            taskStore, taskAgent, mock(TaskApprovalDecisions.class), mock(TaskNotifier.class));

    when(taskAgent.execute(eq(task.getId()), any(TaskReference.class), any(String.class)))
        .thenThrow(new IllegalStateException("token=super-secret at /private/key.txt"));

    assertThatThrownBy(
            () -> execution.execute(task.getId(), TaskExecution.FailureHandling.TERMINAL))
        .isInstanceOf(IllegalStateException.class);

    Task failed = taskStore.getTaskById(task.getId());
    String persisted = Files.readString(Path.of(task.getId()));
    assertThat(failed.getStatus()).isEqualTo(Task.Status.failed);
    assertThat(failed.getFailureDetail())
        .contains("Zalava could not complete this job after multiple attempts.");
    assertThat(persisted)
        .doesNotContain("super-secret")
        .doesNotContain("/private/key.txt")
        .doesNotContain("IllegalStateException");
    assertThat(failed.getAgentFeedback()).isEmpty();
  }

  @Test
  void rejectsTaskThatIsNotQueued() {
    TaskStore taskStore = mock(TaskStore.class);
    Task task =
        new Task(
            "/workspace/tasks/2026-06-08/120000-running.md",
            "Running",
            Instant.now(),
            Task.Status.in_progress,
            "Already running.");
    when(taskStore.getTaskById(task.getId())).thenReturn(task);
    DefaultTaskExecution execution =
        new DefaultTaskExecution(
            taskStore,
            mock(TaskAgent.class),
            mock(TaskApprovalDecisions.class),
            mock(TaskNotifier.class));

    assertThatThrownBy(
            () -> execution.execute(task.getId(), TaskExecution.FailureHandling.RETRYABLE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Only tasks that have status todo can be run");
  }

  private Task task(TaskReference reference, Task.Status status, String description) {
    return new Task(
        "/workspace/tasks/" + reference.path(), "Write file", Instant.now(), status, description);
  }
}
