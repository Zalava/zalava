package org.zalava.tasks.adapter.out.agent;

import org.zalava.assistant.agent.Agent;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.application.port.out.AccountStore;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.application.BoundedTaskAgentLoop;
import org.zalava.tasks.application.port.out.ActorTaskAgent;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.TaskExecutionContext;

/** Executes an actor-owned background job with trusted actor and role propagation. */
public final class ActorAgentTaskAgent implements ActorTaskAgent {
  private final Agent agent;
  private final AccountStore accounts;
  private final ActorExecutionContext actorExecution;
  private final TaskExecutionContext taskExecution;
  private final BoundedTaskAgentLoop loop;

  public ActorAgentTaskAgent(
      Agent agent,
      AccountStore accounts,
      ActorExecutionContext actorExecution,
      TaskExecutionContext taskExecution) {
    this(agent, accounts, actorExecution, taskExecution, new BoundedTaskAgentLoop());
  }

  ActorAgentTaskAgent(
      Agent agent,
      AccountStore accounts,
      ActorExecutionContext actorExecution,
      TaskExecutionContext taskExecution,
      BoundedTaskAgentLoop loop) {
    this.agent = agent;
    this.accounts = accounts;
    this.actorExecution = actorExecution;
    this.taskExecution = taskExecution;
    this.loop = loop;
  }

  @Override
  public Result execute(Actor actor, ActorTaskReference reference, String prompt) {
    var account =
        accounts
            .findById(actor.accountId())
            .filter(value -> value.enabled())
            .orElseThrow(() -> new IllegalStateException("Actor account is unavailable"));
    ActorTaskExecutionReference execution = new ActorTaskExecutionReference(actor, reference);
    return actorExecution.call(
        actor,
        account.role(),
        () ->
            taskExecution.call(
                execution,
                () -> {
                  var result = loop.execute(execution.encode(), prompt, agent::task).result();
                  return new Result(result.newStatus(), result.feedback());
                }));
  }
}
