package org.zalava.tasks.domain;

import static org.assertj.core.api.Assertions.assertThat;

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
            .withStatus(Task.Status.failed)
            .withFailureDetail("The remote service was unavailable.");

    assertThat(task.getGoalDescription()).isEqualTo("Original goal.");
    assertThat(task.getAgentFeedback()).contains("Partial progress.");
    assertThat(task.getFailureDetail()).contains("The remote service was unavailable.");
  }

  @Test
  void boundsAndFlattensFailureDetailForSafeDisplay() {
    Task task =
        Task.newTask("Example", "Original goal.")
            .withStatus(Task.Status.failed)
            .withFailureDetail("  First line.\nSecond line. " + "x".repeat(1000));

    assertThat(task.getFailureDetail())
        .hasValueSatisfying(
            detail -> {
              assertThat(detail).doesNotContain("\n");
              assertThat(detail.length()).isLessThanOrEqualTo(500);
            });
  }

  @Test
  void clearsFailureDetailWhenTaskLeavesFailedStatus() {
    Task completed =
        Task.newTask("Example", "Original goal.")
            .withStatus(Task.Status.failed)
            .withFailureDetail("Generic failure detail.")
            .withStatus(Task.Status.completed);

    assertThat(completed.getFailureDetail()).isEmpty();
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
