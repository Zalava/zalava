package org.zalava.tasks.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.application.port.out.ActorTaskStore;
import org.zalava.tasks.application.port.out.TaskScheduler;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;
import org.zalava.tasks.domain.TaskNotFoundException;

class ActorTaskUseCasesTest {
  @Test
  void createsAndSchedulesOnlyAnOpaqueOwnerBoundToken() {
    CapturingScheduler scheduler = new CapturingScheduler();
    Actor actor = new Actor(AccountId.newId());
    ActorTaskUseCases useCases = new ActorTaskUseCases(new InMemoryActorTaskStore(), scheduler);

    ActorTaskReference reference = useCases.create(actor, "private", "description");

    assertThat(scheduler.enqueued).hasSize(1);
    assertThat(ActorTaskExecutionReference.parse(scheduler.enqueued.getFirst()))
        .isEqualTo(new ActorTaskExecutionReference(actor, reference));
    assertThat(scheduler.enqueued.getFirst()).doesNotContain("/", "\\", "workspace", "tasks");
  }

  @Test
  void listAndGetRejectAnotherActorsReference() {
    CapturingScheduler scheduler = new CapturingScheduler();
    Actor first = new Actor(AccountId.newId());
    Actor second = new Actor(AccountId.newId());
    ActorTaskUseCases useCases = new ActorTaskUseCases(new InMemoryActorTaskStore(), scheduler);
    ActorTaskReference reference = useCases.create(first, "private", "description");

    assertThat(useCases.list(second)).isEmpty();
    assertThatThrownBy(() -> useCases.get(second, reference))
        .isInstanceOf(TaskNotFoundException.class);
  }

  private static final class CapturingScheduler implements TaskScheduler {
    private final List<String> enqueued = new ArrayList<>();

    @Override
    public void enqueue(String taskId) {
      enqueued.add(taskId);
    }

    @Override
    public void schedule(LocalDateTime executionTime, String taskId) {
      enqueued.add(taskId);
    }

    @Override
    public void scheduleRecurring(String name, String cron, String taskId) {}

    @Override
    public void deleteRecurring(String name) {}
  }

  private static final class InMemoryActorTaskStore implements ActorTaskStore {
    private final Map<String, Task> values = new HashMap<>();

    private static String key(Actor actor, ActorTaskReference reference) {
      return actor.accountId() + "/" + reference.value();
    }

    @Override
    public Task save(Actor actor, ActorTaskReference reference, Task task) {
      Task saved =
          new Task(
              reference.value(),
              task.getName(),
              task.getCreatedAt(),
              task.getStatus(),
              task.getGoalDescription(),
              task.getAgentFeedback().orElse(null),
              task.getFailureDetail().orElse(null));
      values.put(key(actor, reference), saved);
      return saved;
    }

    @Override
    public Task get(Actor actor, ActorTaskReference reference) {
      Task task = values.get(key(actor, reference));
      if (task == null)
        throw new TaskNotFoundException(reference.value(), new java.io.IOException("not found"));
      return task;
    }

    @Override
    public List<ActorTaskReference> list(Actor actor) {
      return values.keySet().stream()
          .filter(key -> key.startsWith(actor.accountId() + "/"))
          .map(key -> new ActorTaskReference(key.substring(key.indexOf('/') + 1)))
          .toList();
    }
  }
}
