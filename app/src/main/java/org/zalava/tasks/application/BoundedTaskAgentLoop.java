package org.zalava.tasks.application;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;
import org.zalava.tasks.application.port.out.TaskAgent;
import org.zalava.tasks.domain.Task;

/**
 * SEA-owned task-agent state machine.
 *
 * <p>The model adapter supplies one turn at a time. It cannot decide whether a run may continue:
 * this loop owns iteration and elapsed-time limits, terminal state mapping, and the feedback that
 * is supplied to a subsequent turn after a recoverable tool failure.
 */
public final class BoundedTaskAgentLoop {

  public static final int DEFAULT_MAX_ITERATIONS = 4;
  public static final Duration DEFAULT_MAX_ELAPSED = Duration.ofMinutes(2);

  private final int maxIterations;
  private final Duration maxElapsed;
  private final Supplier<Instant> clock;

  public BoundedTaskAgentLoop() {
    this(DEFAULT_MAX_ITERATIONS, DEFAULT_MAX_ELAPSED, Instant::now);
  }

  public BoundedTaskAgentLoop(int maxIterations, Duration maxElapsed, Supplier<Instant> clock) {
    if (maxIterations < 1) {
      throw new IllegalArgumentException("maxIterations must be positive");
    }
    if (maxElapsed.isNegative() || maxElapsed.isZero()) {
      throw new IllegalArgumentException("maxElapsed must be positive");
    }
    this.maxIterations = maxIterations;
    this.maxElapsed = Objects.requireNonNull(maxElapsed, "maxElapsed");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public Result execute(String taskId, String prompt, Turn turn) {
    Objects.requireNonNull(taskId, "taskId");
    Objects.requireNonNull(prompt, "prompt");
    Objects.requireNonNull(turn, "turn");
    Instant startedAt = clock.get();
    String feedback = prompt;
    for (int iteration = 1; iteration <= maxIterations; iteration++) {
      if (elapsedSince(startedAt).compareTo(maxElapsed) >= 0) {
        return Result.budgetExhausted(iteration - 1);
      }
      try {
        TaskAgent.Result result =
            Objects.requireNonNull(turn.execute(taskId, feedback), "turn result");
        if (isTerminal(result.newStatus())) {
          return Result.completed(result, iteration, stopReason(result.newStatus()));
        }
        feedback = result.feedback();
      } catch (ToolFailure failure) {
        feedback = "A tool call failed: " + failure.feedback();
      }
    }
    return Result.iterationLimit(maxIterations);
  }

  private Duration elapsedSince(Instant startedAt) {
    return Duration.between(startedAt, clock.get());
  }

  private static boolean isTerminal(Task.Status status) {
    return switch (status) {
      case completed, awaiting_human_input, cancelled, failed -> true;
      case todo, in_progress -> false;
    };
  }

  private static StopReason stopReason(Task.Status status) {
    return switch (status) {
      case completed -> StopReason.SUCCESS;
      case awaiting_human_input -> StopReason.APPROVAL_PAUSE;
      case cancelled -> StopReason.CANCELLED;
      case failed -> StopReason.FAILURE;
      case todo, in_progress ->
          throw new IllegalArgumentException("non-terminal status: " + status);
    };
  }

  @FunctionalInterface
  public interface Turn {
    TaskAgent.Result execute(String taskId, String feedback) throws ToolFailure;
  }

  public enum StopReason {
    SUCCESS,
    APPROVAL_PAUSE,
    CANCELLED,
    FAILURE,
    ITERATION_LIMIT,
    BUDGET_EXHAUSTED
  }

  public record Result(TaskAgent.Result result, int iterations, StopReason stopReason) {
    private static Result completed(
        TaskAgent.Result result, int iterations, StopReason stopReason) {
      return new Result(result, iterations, stopReason);
    }

    private static Result iterationLimit(int iterations) {
      return new Result(
          new TaskAgent.Result(Task.Status.failed, "Task-agent iteration limit reached."),
          iterations,
          StopReason.ITERATION_LIMIT);
    }

    private static Result budgetExhausted(int iterations) {
      return new Result(
          new TaskAgent.Result(Task.Status.failed, "Task-agent elapsed-time budget exhausted."),
          iterations,
          StopReason.BUDGET_EXHAUSTED);
    }
  }

  /**
   * A recoverable tool execution failure whose bounded feedback is safe to show to the next turn.
   */
  public static final class ToolFailure extends RuntimeException {
    private final String feedback;

    public ToolFailure(String feedback) {
      super(feedback);
      this.feedback = Objects.requireNonNull(feedback, "feedback");
    }

    public String feedback() {
      return feedback;
    }
  }
}
