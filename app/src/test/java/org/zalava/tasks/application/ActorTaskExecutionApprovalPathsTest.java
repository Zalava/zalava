package org.zalava.tasks.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;
import org.zalava.tasks.application.port.out.ActorTaskAgent;
import org.zalava.tasks.application.port.out.ActorTaskApprovalDecisions;
import org.zalava.tasks.application.port.out.ActorTaskNotifier;
import org.zalava.tasks.application.port.out.ActorTaskStore;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;
import org.zalava.tasks.domain.TaskNotFoundException;
import org.junit.jupiter.api.Test;

class ActorTaskExecutionApprovalPathsTest {

  private final Actor owner = new Actor(AccountId.newId());
  private final ActorTaskReference reference = ActorTaskReference.newReference();
  private final RecordingStore store = new RecordingStore();
  private final RecordingNotifier notifier = new RecordingNotifier();

  private String token() {
    return new ActorTaskExecutionReference(owner, reference).encode();
  }

  @Test
  void pendingApprovalsParkTheTaskAwaitingHumanInput() {
    store.save(owner, reference, Task.newTask("work", "goal"));
    ActorTaskApprovalDecisions approvals =
        new ActorTaskApprovalDecisions() {
          @Override
          public boolean hasPending(Actor actor, ActorTaskReference taskReference) {
            return true;
          }

          @Override
          public List<PendingApproval> pendingFor(Actor actor, ActorTaskReference taskReference) {
            return List.of(
                new PendingApproval(
                    "req-1",
                    "files",
                    "write",
                    "Write to report.md",
                    "side-effecting",
                    "{\"path\":\"report.md\"}",
                    Map.of(),
                    List.of()));
          }

          @Override
          public List<Decision> unconsumedFor(Actor actor, ActorTaskReference taskReference) {
            return List.of();
          }
        };
    ActorTaskExecution execution =
        new ActorTaskExecution(
            store,
            (actor, ref, prompt) -> new ActorTaskAgent.Result(Task.Status.completed, "done"),
            notifier,
            approvals);

    Task.Status status = execution.execute(token());

    assertThat(status).isEqualTo(Task.Status.awaiting_human_input);
    assertThat(store.get(owner, reference).getStatus()).isEqualTo(Task.Status.awaiting_human_input);
    assertThat(store.get(owner, reference).getAgentFeedback())
        .contains("Request req-1: Write to report.md (files/write, {\"path\":\"report.md\"})");
  }

  @Test
  void pendingApprovalsWithoutDetailsProduceAGenericWaitingFeedback() {
    store.save(owner, reference, Task.newTask("work", "goal"));
    ActorTaskApprovalDecisions approvals =
        new ActorTaskApprovalDecisions() {
          @Override
          public boolean hasPending(Actor actor, ActorTaskReference taskReference) {
            return true;
          }

          @Override
          public List<PendingApproval> pendingFor(Actor actor, ActorTaskReference taskReference) {
            return List.of();
          }

          @Override
          public List<Decision> unconsumedFor(Actor actor, ActorTaskReference taskReference) {
            return List.of();
          }
        };
    ActorTaskExecution execution =
        new ActorTaskExecution(
            store,
            (actor, ref, prompt) -> new ActorTaskAgent.Result(Task.Status.completed, "done"),
            notifier,
            approvals);

    execution.execute(token());

    assertThat(store.get(owner, reference).getAgentFeedback())
        .contains("Waiting for approval of a side-effecting SEA tool call.");
  }

  @Test
  void unconsumedDecisionsAreReplayedIntoTheAgentPrompt() {
    store.save(owner, reference, Task.newTask("work", "goal"));
    List<String> prompts = new java.util.ArrayList<>();
    ActorTaskApprovalDecisions approvals =
        new ActorTaskApprovalDecisions() {
          @Override
          public boolean hasPending(Actor actor, ActorTaskReference taskReference) {
            return false;
          }

          @Override
          public List<PendingApproval> pendingFor(Actor actor, ActorTaskReference taskReference) {
            return List.of();
          }

          @Override
          public List<Decision> unconsumedFor(Actor actor, ActorTaskReference taskReference) {
            return List.of(
                new Decision("ALLOWED", "files", "write", "{\"path\":\"report.md\"}"),
                new Decision("DENIED", "shell", "execute", "{\"cmd\":\"rm\"}"));
          }
        };
    ActorTaskExecution execution =
        new ActorTaskExecution(
            store,
            (actor, ref, prompt) -> {
              prompts.add(prompt);
              return new ActorTaskAgent.Result(Task.Status.completed, "done");
            },
            notifier,
            approvals);

    execution.execute(token());

    assertThat(prompts).hasSize(1);
    assertThat(prompts.getFirst())
        .contains("Approval decisions since the previous attempt:")
        .contains("- ALLOWED files/write with arguments {\"path\":\"report.md\"}")
        .contains("- DENIED shell/execute with arguments {\"cmd\":\"rm\"}")
        .contains("Retry an allowed tool call with the same arguments.")
        .contains("Do not execute a denied operation.");
  }

  @Test
  void agentFailureRollsTheTaskBackToTodoAndRethrows() {
    store.save(owner, reference, Task.newTask("work", "goal"));
    ActorTaskExecution execution =
        new ActorTaskExecution(
            store,
            (actor, ref, prompt) -> {
              throw new IllegalStateException("agent exploded");
            },
            notifier);

    assertThatThrownBy(() -> execution.execute(token()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("agent exploded");

    assertThat(store.get(owner, reference).getStatus()).isEqualTo(Task.Status.todo);
    assertThat(notifier.count).isZero();
  }

  @Test
  void nonTodoTasksRefuseExecution() {
    store.save(owner, reference, Task.newTask("work", "goal").withStatus(Task.Status.completed));

    assertThatThrownBy(() -> executionForEmptyApprovals().execute(token()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Only queued actor tasks can execute");
  }

  @Test
  void promptWithoutApprovalsContainsOnlyTheTaskGoal() {
    store.save(owner, reference, Task.newTask("work", "goal"));
    List<String> prompts = new java.util.ArrayList<>();
    ActorTaskExecution execution =
        new ActorTaskExecution(
            store,
            (actor, ref, prompt) -> {
              prompts.add(prompt);
              return new ActorTaskAgent.Result(Task.Status.completed, "done");
            },
            notifier);

    execution.execute(token());

    assertThat(prompts.getFirst()).isEqualTo("Handle task 'work': goal");
  }

  private ActorTaskExecution executionForEmptyApprovals() {
    return new ActorTaskExecution(store, (a, r, p) -> null, notifier);
  }

  private static final class RecordingStore implements ActorTaskStore {
    private final Map<String, Task> values = new HashMap<>();

    private static String key(Actor actor, ActorTaskReference ref) {
      return actor.accountId() + ":" + ref.value();
    }

    @Override
    public Task save(Actor actor, ActorTaskReference ref, Task task) {
      Task saved =
          new Task(
              ref.value(),
              task.getName(),
              task.getCreatedAt(),
              task.getStatus(),
              task.getGoalDescription(),
              task.getAgentFeedback().orElse(null),
              task.getFailureDetail().orElse(null));
      values.put(key(actor, ref), saved);
      return saved;
    }

    @Override
    public Task get(Actor actor, ActorTaskReference ref) {
      Task task = values.get(key(actor, ref));
      if (task == null)
        throw new TaskNotFoundException(ref.value(), new java.io.IOException("not found"));
      return task;
    }

    @Override
    public List<ActorTaskReference> list(Actor actor) {
      return List.of();
    }
  }

  private static final class RecordingNotifier implements ActorTaskNotifier {
    private int count;

    @Override
    public void notify(Actor actor, String name, Task.Status status, String feedback) {
      count++;
    }
  }
}
