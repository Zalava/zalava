package org.zalava.tasks.adapter.out.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.zalava.assistant.agent.Agent;
import org.zalava.tasks.application.BoundedTaskAgentLoop;
import org.zalava.tasks.application.port.out.TaskAgent;
import org.zalava.tasks.domain.Task;
import org.zalava.tasks.domain.TaskExecutionContext;
import org.zalava.tasks.domain.TaskReference;

class AgentTaskAgentTest {

  @Test
  void suppliesTaskReferenceOnlyWhileAgentExecutes() {
    Agent agent = mock(Agent.class);
    TaskExecutionContext executionContext = new TaskExecutionContext();
    AgentTaskAgent taskAgent = new AgentTaskAgent(agent, executionContext);
    TaskReference reference = TaskReference.parse("2026-06-13", "120000-task.md");
    TaskAgent.Result expected = new TaskAgent.Result(Task.Status.completed, "Done");
    when(agent.task(eq("task-id"), eq("prompt")))
        .thenAnswer(
            invocation -> {
              assertThat(executionContext.currentTaskReference()).contains(reference);
              return expected;
            });

    TaskAgent.Result actual = taskAgent.execute("task-id", reference, "prompt");

    assertThat(actual).isEqualTo(expected);
    assertThat(executionContext.currentTaskReference()).isEmpty();
  }

  @Test
  void returnsBoundedLoopFailureWhenModelNeverTerminates() {
    Agent agent = mock(Agent.class);
    TaskExecutionContext executionContext = new TaskExecutionContext();
    AgentTaskAgent taskAgent =
        new AgentTaskAgent(
            agent,
            executionContext,
            new BoundedTaskAgentLoop(1, java.time.Duration.ofMinutes(1), java.time.Instant::now));
    TaskReference reference = TaskReference.parse("2026-06-13", "120000-task.md");
    when(agent.task(eq("task-id"), eq("prompt")))
        .thenReturn(new TaskAgent.Result(Task.Status.in_progress, "continuing"));

    TaskAgent.Result result = taskAgent.execute("task-id", reference, "prompt");

    assertThat(result.newStatus()).isEqualTo(Task.Status.failed);
    assertThat(result.feedback()).contains("iteration limit");
  }
}
