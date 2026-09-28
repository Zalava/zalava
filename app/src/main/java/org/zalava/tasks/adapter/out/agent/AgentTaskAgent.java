package org.zalava.tasks.adapter.out.agent;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.zalava.agent.Agent;
import org.zalava.tasks.application.BoundedTaskAgentLoop;
import org.zalava.tasks.application.port.out.TaskAgent;
import org.zalava.tasks.domain.TaskExecutionContext;
import org.zalava.tasks.domain.TaskReference;

@Component
public class AgentTaskAgent implements TaskAgent {

  private final Agent agent;
  private final TaskExecutionContext taskExecutionContext;
  private final BoundedTaskAgentLoop loop;

  @Autowired
  public AgentTaskAgent(Agent agent, TaskExecutionContext taskExecutionContext) {
    this(agent, taskExecutionContext, new BoundedTaskAgentLoop());
  }

  AgentTaskAgent(
      Agent agent, TaskExecutionContext taskExecutionContext, BoundedTaskAgentLoop loop) {
    this.agent = agent;
    this.taskExecutionContext = taskExecutionContext;
    this.loop = loop;
  }

  @Override
  public Result execute(String taskId, TaskReference taskReference, String prompt) {
    return taskExecutionContext.call(
        taskReference,
        () -> {
          BoundedTaskAgentLoop.Result result = loop.execute(taskId, prompt, agent::task);
          return result.result();
        });
  }
}
