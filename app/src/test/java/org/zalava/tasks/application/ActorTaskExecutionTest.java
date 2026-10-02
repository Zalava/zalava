package org.zalava.tasks.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.application.port.out.ActorTaskAgent;
import org.zalava.tasks.application.port.out.ActorTaskNotifier;
import org.zalava.tasks.application.port.out.ActorTaskStore;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;
import org.zalava.tasks.domain.TaskNotFoundException;

class ActorTaskExecutionTest {
  @Test
  void executesAndNotifiesOnlyTheTokenOwner() {
    Actor owner = new Actor(AccountId.newId());
    ActorTaskReference reference = ActorTaskReference.newReference();
    Store store = new Store();
    store.save(owner, reference, Task.newTask("private", "work"));
    CapturingNotifier notifier = new CapturingNotifier();
    ActorTaskExecution execution =
        new ActorTaskExecution(
            store,
            (actor, task, prompt) -> new ActorTaskAgent.Result(Task.Status.completed, "done"),
            notifier);

    Task.Status status =
        execution.execute(new ActorTaskExecutionReference(owner, reference).encode());

    assertThat(status).isEqualTo(Task.Status.completed);
    assertThat(store.get(owner, reference).getAgentFeedback()).contains("done");
    assertThat(notifier.actor).isEqualTo(owner);
  }

  @Test
  void rejectsATokenForAnotherOwnersTask() {
    Actor owner = new Actor(AccountId.newId());
    Actor other = new Actor(AccountId.newId());
    ActorTaskReference reference = ActorTaskReference.newReference();
    Store store = new Store();
    store.save(owner, reference, Task.newTask("private", "work"));
    ActorTaskExecution execution =
        new ActorTaskExecution(store, (a, r, p) -> null, (a, n, s, f) -> {});

    assertThatThrownBy(
            () -> execution.execute(new ActorTaskExecutionReference(other, reference).encode()))
        .isInstanceOf(TaskNotFoundException.class);
  }

  private static final class CapturingNotifier implements ActorTaskNotifier {
    private Actor actor;

    @Override
    public void notify(Actor actor, String name, Task.Status status, String feedback) {
      this.actor = actor;
    }
  }

  private static final class Store implements ActorTaskStore {
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
}
