package org.zalava.tasks.adapter.out.clarification;

import java.util.List;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.application.port.out.ActorTaskClarifications;
import org.zalava.tasks.clarification.ClarificationText;
import org.zalava.tasks.clarification.SeaClarifications;
import org.zalava.tasks.domain.ActorTaskReference;

/** Owner-scoped clarification view for actor task execution. */
public final class SeaActorTaskClarifications implements ActorTaskClarifications {
  private final SeaClarifications clarifications;

  public SeaActorTaskClarifications(SeaClarifications clarifications) {
    this.clarifications = clarifications;
  }

  @Override
  public boolean hasPending(Actor actor, ActorTaskReference taskReference) {
    return clarifications.hasPending(actor, taskReference);
  }

  @Override
  public List<PendingClarification> pendingFor(Actor actor, ActorTaskReference taskReference) {
    return clarifications.pendingFor(actor, taskReference).stream()
        .map(
            request ->
                new PendingClarification(
                    request.requestId(),
                    request.prompt(),
                    request.questions().stream()
                        .flatMap(question -> question.choices().stream())
                        .map(choice -> choice.label())
                        .toList()))
        .toList();
  }

  @Override
  public List<ResolvedClarification> resolvedFor(Actor actor, ActorTaskReference taskReference) {
    return clarifications.resolvedFor(actor, taskReference).stream()
        .map(
            request ->
                new ResolvedClarification(
                    request.requestId(),
                    ClarificationText.answerSummary(request),
                    request.status().name()))
        .toList();
  }
}
