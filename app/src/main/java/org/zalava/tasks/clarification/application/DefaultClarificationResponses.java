package org.zalava.tasks.clarification.application;

import java.util.List;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.application.port.in.ActorTaskCommands;
import org.zalava.tasks.clarification.SeaClarifications;
import org.zalava.tasks.clarification.application.port.in.ClarificationResponses;
import org.zalava.tasks.clarification.domain.ClarificationRequest;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.Task;

/**
 * Applies an actor-owned clarification response and resumes the paused job through the existing
 * task loop once no clarification or approval remains pending.
 */
public final class DefaultClarificationResponses implements ClarificationResponses {
  private final SeaClarifications clarifications;
  private final ActorTaskCommands tasks;

  public DefaultClarificationResponses(SeaClarifications clarifications, ActorTaskCommands tasks) {
    this.clarifications = clarifications;
    this.tasks = tasks;
  }

  @Override
  public ClarificationRequest answer(
      Actor actor, String requestId, List<ClarificationRequest.Answer> answers) {
    ClarificationRequest request = clarifications.answer(actor, requestId, answers);
    resumeIfIdle(actor, request);
    return request;
  }

  @Override
  public ClarificationRequest cancel(Actor actor, String requestId) {
    ClarificationRequest request = clarifications.cancel(actor, requestId);
    resumeIfIdle(actor, request);
    return request;
  }

  @Override
  public List<ClarificationRequest> pending(Actor actor) {
    return clarifications.pending(actor);
  }

  @Override
  public ClarificationRequest get(Actor actor, String requestId) {
    return clarifications.get(actor, requestId);
  }

  private void resumeIfIdle(Actor actor, ClarificationRequest request) {
    ActorTaskReference reference = new ActorTaskReference(request.taskReference());
    if (tasks.get(actor, reference).getStatus() != Task.Status.awaiting_human_input) return;
    tasks.resume(actor, reference);
  }
}
