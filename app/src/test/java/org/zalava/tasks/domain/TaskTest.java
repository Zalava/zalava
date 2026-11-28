package org.zalava.tasks.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TaskTest {

  @Test
  void separatesPersistedGoalAndAgentFeedback() {
    Task task =
        new Task(
            "/workspace/tasks/2026-06-08/120000-example.md",
            "Example",
            Instant.parse("2026-06-08T12:00:00Z"),
            Task.Status.completed,
            "Original goal.\n\nAgent feedback: Finished the requested work.");

    assertThat(task.getGoalDescription()).isEqualTo("Original goal.");
    assertThat(task.getAgentFeedback()).contains("Finished the requested work.");
  }

  @Test
  void returnsOriginalDescriptionWhenFeedbackIsAbsent() {
    Task task = Task.newTask("Example", "Original goal.");

    assertThat(task.getGoalDescription()).isEqualTo("Original goal.");
    assertThat(task.getAgentFeedback()).isEmpty();
  }

  @Test
  void separatesFeedbackWhenYamlHasFoldedTheBlankLine() {
    Task task =
        new Task(
            "/workspace/tasks/2026-06-08/120000-example.md",
            "Example",
            Instant.parse("2026-06-08T12:00:00Z"),
            Task.Status.completed,
            "Original goal. Agent feedback: Finished the requested work.");

    assertThat(task.getGoalDescription()).isEqualTo("Original goal.");
    assertThat(task.getAgentFeedback()).contains("Finished the requested work.");
  }

  @Test
  void replacesPreviousFeedbackWithoutChangingTheGoal() {
    Task task =
        Task.newTask("Example", "Original goal.")
            .withFeedback("Waiting for input.")
            .withFeedback("Finished after approval.");

    assertThat(task.getGoalDescription()).isEqualTo("Original goal.");
    assertThat(task.getAgentFeedback()).contains("Finished after approval.");
    assertThat(task.getDescription()).isEqualTo("Original goal.");
  }

  @Test
  void keepsFailureDetailSeparateFromGoalAndFeedback() {
    Task task =
        Task.newTask("Example", "Original goal.")
            .withFeedback("Partial progress.")
            .start()
            .fail("The remote service was unavailable.");

    assertThat(task.getGoalDescription()).isEqualTo("Original goal.");
    assertThat(task.getAgentFeedback()).contains("Partial progress.");
    assertThat(task.getFailureDetail()).contains("The remote service was unavailable.");
  }

  @Test
  void boundsAndFlattensFailureDetailForSafeDisplay() {
    Task task =
        Task.newTask("Example", "Original goal.")
            .start()
            .fail("  First line.\nSecond line. " + "x".repeat(1000));

    assertThat(task.getFailureDetail())
        .hasValueSatisfying(
            detail -> {
              assertThat(detail).doesNotContain("\n");
              assertThat(detail.length()).isLessThanOrEqualTo(500);
            });
  }

  @Test
  void aFailedTaskRejectsFurtherExecutionTransitions() {
    Task failed = Task.newTask("Example", "Original goal.").start().fail("Recorded failure");

    assertThatThrownBy(failed::start).isInstanceOf(Task.NotQueuedException.class);
    assertThatThrownBy(failed::resume).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(failed::retry).isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> failed.fail("Different failure"))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> failed.recordAttemptResult(Task.Status.completed, "Done"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void preservesTaskIdentityAndFeedbackWhenResumingAndRetrying() {
    Instant createdAt = Instant.parse("2026-06-08T12:00:00Z");
    Task queued = new Task("task-id", "Example", createdAt, Task.Status.todo, "Original goal.");
    Task paused = queued.start().recordAttemptResult(Task.Status.awaiting_human_input, " Waiting ");
    Task resumed = paused.resume();
    Task retried = resumed.start().retry();

    assertThat(paused.isAwaitingHumanInput()).isTrue();
    assertThat(resumed.getStatus()).isEqualTo(Task.Status.todo);
    assertThat(retried.getStatus()).isEqualTo(Task.Status.todo);
    assertThat(retried.getId()).isEqualTo(queued.getId());
    assertThat(retried.getCreatedAt()).isEqualTo(createdAt);
    assertThat(retried.getGoalDescription()).isEqualTo("Original goal.");
    assertThat(retried.getAgentFeedback()).contains("Waiting");
  }

  @Test
  void ignoresFailureDetailForAConstructedNonFailedTask() {
    Task inconsistent =
        new Task(
            "/workspace/tasks/2026-06-08/120000-example.md",
            "Example",
            Instant.parse("2026-06-08T12:00:00Z"),
            Task.Status.completed,
            "Original goal.",
            "Completed normally.",
            "Should not remain visible.");

    assertThat(inconsistent.getFailureDetail()).isEmpty();
  }
}
