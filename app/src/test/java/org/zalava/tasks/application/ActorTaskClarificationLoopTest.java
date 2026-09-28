package org.zalava.tasks.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;
import org.zalava.clarification.SeaClarifications;
import org.zalava.clarification.application.DefaultClarificationResponses;
import org.zalava.clarification.application.port.out.ClarificationStore;
import org.zalava.clarification.domain.ClarificationDraft;
import org.zalava.clarification.domain.ClarificationRequest;
import org.zalava.tasks.adapter.out.clarification.SeaActorTaskClarifications;
import org.zalava.tasks.application.port.in.ActorTaskCommands;
import org.zalava.tasks.application.port.out.ActorTaskAgent;
import org.zalava.tasks.application.port.out.ActorTaskClarifications;
import org.zalava.tasks.application.port.out.ActorTaskNotifier;
import org.zalava.tasks.application.port.out.ActorTaskStore;
import org.zalava.tasks.application.port.out.TaskAgent;
import org.zalava.tasks.application.port.out.TaskScheduler;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;
import org.zalava.tasks.domain.TaskNotFoundException;
import org.junit.jupiter.api.Test;

/**
 * Proves typed clarification pauses and resumes the existing bounded task loop rather than a
 * parallel loop: a pending clarification parks the job, the owner's answer is replayed into the
 * next prompt, and a job that keeps asking for input is still bounded by the loop limits.
 */
class ActorTaskClarificationLoopTest {

  private final Actor owner = new Actor(AccountId.newId());
  private final ActorTaskReference reference = ActorTaskReference.newReference();
  private final RecordingStore store = new RecordingStore();
  private final RecordingNotifier notifier = new RecordingNotifier();
  private final SeaClarifications clarifications =
      new SeaClarifications(new InMemoryStore(), Instant::now);
  private final ActorTaskClarifications taskClarifications =
      new SeaActorTaskClarifications(clarifications);

  @Test
  void aPendingClarificationParksTheTaskAwaitingHumanInput() {
    store.save(owner, reference, Task.newTask("work", "goal"));
    ActorTaskExecution execution =
        new ActorTaskExecution(
            store,
            (actor, ref, prompt) -> {
              clarifications.create(actor, ref, draft());
              return new ActorTaskAgent.Result(Task.Status.completed, "done");
            },
            notifier,
            null,
            taskClarifications);

    Task.Status status = execution.execute(token());

    assertThat(status).isEqualTo(Task.Status.awaiting_human_input);
    String feedback = store.get(owner, reference).getAgentFeedback().orElseThrow();
    assertThat(feedback)
        .contains("Clarification request")
        .contains("Which report?")
        .contains("Monthly");
    assertThat(clarifications.pendingFor(owner, reference)).hasSize(1);
  }

  @Test
  void theOwnersAnswerIsReplayedIntoTheNextPrompt() {
    store.save(owner, reference, Task.newTask("work", "goal"));
    ClarificationRequest request = clarifications.create(owner, reference, draft());
    clarifications.answer(
        owner, request.requestId(), List.of(new ClarificationRequest.Answer("q1", "a", null)));
    List<String> prompts = new ArrayList<>();
    ActorTaskExecution execution =
        new ActorTaskExecution(
            store,
            (actor, ref, prompt) -> {
              prompts.add(prompt);
              return new ActorTaskAgent.Result(Task.Status.completed, "done");
            },
            notifier,
            null,
            taskClarifications);

    execution.execute(token());

    assertThat(prompts).hasSize(1);
    assertThat(prompts.getFirst())
        .contains("Clarification answers since the previous attempt:")
        .contains("Q: Which report? A: Monthly")
        .contains("do not ask again for the same information");
  }

  @Test
  void anAgentThatKeepsAskingIsBoundedByTheLoopAndDeduplicatesTheRequest() {
    store.save(owner, reference, Task.newTask("work", "goal"));
    BoundedTaskAgentLoop loop = new BoundedTaskAgentLoop(3, Duration.ofMinutes(1), Instant::now);
    List<Integer> invocations = new ArrayList<>();

    BoundedTaskAgentLoop.Result result =
        loop.execute(
            "task",
            "prompt",
            (taskId, feedback) -> {
              invocations.add(1);
              clarifications.create(owner, reference, draft());
              return new TaskAgent.Result(Task.Status.in_progress, "still asking");
            });

    assertThat(invocations).hasSize(3);
    assertThat(result.iterations()).isEqualTo(3);
    assertThat(result.stopReason()).isEqualTo(BoundedTaskAgentLoop.StopReason.ITERATION_LIMIT);
    assertThat(clarifications.pendingFor(owner, reference)).hasSize(1);
  }

  @Test
  void answeringResumesThePausedJobOnlyOnce() {
    store.save(
        owner,
        reference,
        Task.newTask("work", "goal").withStatus(Task.Status.awaiting_human_input));
    ClarificationRequest request = clarifications.create(owner, reference, draft());
    RecordingScheduler scheduler = new RecordingScheduler();
    ActorTaskCommands commands = new ActorTaskUseCases(store, scheduler);
    DefaultClarificationResponses responses =
        new DefaultClarificationResponses(clarifications, commands);

    responses.answer(
        owner, request.requestId(), List.of(new ClarificationRequest.Answer("q1", "a", null)));
    responses.answer(
        owner, request.requestId(), List.of(new ClarificationRequest.Answer("q1", "a", null)));

    assertThat(store.get(owner, reference).getStatus()).isEqualTo(Task.Status.todo);
    assertThat(scheduler.enqueued).hasSize(1);
  }

  private String token() {
    return new ActorTaskExecutionReference(owner, reference).encode();
  }

  private static ClarificationDraft draft() {
    return new ClarificationDraft(
        "Which report?",
        List.of(
            new ClarificationDraft.Question(
                "q1",
                "Which report?",
                List.of(
                    new ClarificationDraft.Choice("a", "Monthly"),
                    new ClarificationDraft.Choice("b", "Weekly")),
                false)));
  }

  private static final class InMemoryStore implements ClarificationStore {
    private final List<ClarificationRequest> values = new ArrayList<>();

    @Override
    public List<ClarificationRequest> load() {
      return List.copyOf(values);
    }

    @Override
    public void save(ClarificationRequest request) {
      values.removeIf(value -> value.requestId().equals(request.requestId()));
      values.add(request);
    }

    @Override
    public void delete(String requestId) {
      values.removeIf(value -> value.requestId().equals(requestId));
    }
  }

  private static final class RecordingScheduler implements TaskScheduler {
    private final List<String> enqueued = new ArrayList<>();

    @Override
    public void enqueue(String taskId) {
      enqueued.add(taskId);
    }

    @Override
    public void schedule(LocalDateTime executionTime, String taskId) {}

    @Override
    public void scheduleRecurring(
        String recurringTaskName, String cronExpression, String recurringTaskId) {}

    @Override
    public void deleteRecurring(String recurringTaskName) {}
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
      if (task == null) throw new TaskNotFoundException(ref.value(), new IOException("not found"));
      return task;
    }

    @Override
    public List<ActorTaskReference> list(Actor actor) {
      return List.of();
    }
  }

  private static final class RecordingNotifier implements ActorTaskNotifier {
    @Override
    public void notify(Actor actor, String name, Task.Status status, String feedback) {}
  }
}
