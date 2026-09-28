package org.zalava.tasks.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.zalava.tasks.application.port.out.TaskAgent;
import org.zalava.tasks.domain.Task;

class BoundedTaskAgentLoopTest {

  @Test
  void stopsOnSuccessfulResult() {
    BoundedTaskAgentLoop loop = loop(4, List.of(Instant.EPOCH, Instant.EPOCH));

    BoundedTaskAgentLoop.Result result =
        loop.execute("task-1", "do work", (id, feedback) -> completed("done"));

    assertThat(result.stopReason()).isEqualTo(BoundedTaskAgentLoop.StopReason.SUCCESS);
    assertThat(result.iterations()).isOne();
    assertThat(result.result()).isEqualTo(completed("done"));
  }

  @Test
  void pausesForApprovalWithoutStartingAnotherTurn() {
    BoundedTaskAgentLoop loop = loop(4, List.of(Instant.EPOCH, Instant.EPOCH));

    BoundedTaskAgentLoop.Result result =
        loop.execute(
            "task-1",
            "do work",
            (id, feedback) ->
                new TaskAgent.Result(Task.Status.awaiting_human_input, "approval pending"));

    assertThat(result.stopReason()).isEqualTo(BoundedTaskAgentLoop.StopReason.APPROVAL_PAUSE);
    assertThat(result.iterations()).isOne();
  }

  @Test
  void stopsWhenCancelled() {
    BoundedTaskAgentLoop loop = loop(4, List.of(Instant.EPOCH, Instant.EPOCH));

    BoundedTaskAgentLoop.Result result =
        loop.execute(
            "task-1",
            "do work",
            (id, feedback) -> new TaskAgent.Result(Task.Status.cancelled, "cancelled"));

    assertThat(result.stopReason()).isEqualTo(BoundedTaskAgentLoop.StopReason.CANCELLED);
  }

  @Test
  void feedsToolFailureToNextIteration() {
    BoundedTaskAgentLoop loop = loop(4, List.of(Instant.EPOCH, Instant.EPOCH, Instant.EPOCH));
    List<String> inputs = new java.util.ArrayList<>();

    BoundedTaskAgentLoop.Result result =
        loop.execute(
            "task-1",
            "do work",
            (id, feedback) -> {
              inputs.add(feedback);
              if (inputs.size() == 1) {
                throw new BoundedTaskAgentLoop.ToolFailure("write is unavailable");
              }
              return completed("adapted");
            });

    assertThat(result.stopReason()).isEqualTo(BoundedTaskAgentLoop.StopReason.SUCCESS);
    assertThat(inputs).containsExactly("do work", "A tool call failed: write is unavailable");
  }

  @Test
  void failsDeterministicallyAtIterationLimit() {
    BoundedTaskAgentLoop loop = loop(2, List.of(Instant.EPOCH, Instant.EPOCH, Instant.EPOCH));

    BoundedTaskAgentLoop.Result result =
        loop.execute(
            "task-1",
            "do work",
            (id, feedback) -> new TaskAgent.Result(Task.Status.in_progress, "continue"));

    assertThat(result.stopReason()).isEqualTo(BoundedTaskAgentLoop.StopReason.ITERATION_LIMIT);
    assertThat(result.result().newStatus()).isEqualTo(Task.Status.failed);
    assertThat(result.iterations()).isEqualTo(2);
  }

  @Test
  void failsBeforeATurnWhenElapsedBudgetIsExhausted() {
    BoundedTaskAgentLoop loop = loop(2, List.of(Instant.EPOCH, Instant.EPOCH.plusSeconds(61)));

    BoundedTaskAgentLoop.Result result =
        loop.execute("task-1", "do work", (id, feedback) -> completed("unused"));

    assertThat(result.stopReason()).isEqualTo(BoundedTaskAgentLoop.StopReason.BUDGET_EXHAUSTED);
    assertThat(result.iterations()).isZero();
  }

  private static BoundedTaskAgentLoop loop(int maxIterations, List<Instant> instants) {
    ArrayDeque<Instant> clock = new ArrayDeque<>(instants);
    return new BoundedTaskAgentLoop(maxIterations, Duration.ofMinutes(1), clock::removeFirst);
  }

  private static TaskAgent.Result completed(String feedback) {
    return new TaskAgent.Result(Task.Status.completed, feedback);
  }
}
