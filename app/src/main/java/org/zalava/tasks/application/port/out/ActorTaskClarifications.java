package org.zalava.tasks.application.port.out;

import java.util.List;
import org.zalava.accounts.domain.Actor;
import org.zalava.tasks.domain.ActorTaskReference;

/** Owner-scoped clarification state visible to the trusted task scheduler. */
public interface ActorTaskClarifications {
  boolean hasPending(Actor actor, ActorTaskReference taskReference);

  List<PendingClarification> pendingFor(Actor actor, ActorTaskReference taskReference);

  List<ResolvedClarification> resolvedFor(Actor actor, ActorTaskReference taskReference);

  record PendingClarification(String requestId, String prompt, List<String> choices) {
    public PendingClarification {
      choices = choices == null ? List.of() : List.copyOf(choices);
    }
  }

  record ResolvedClarification(String requestId, String summary, String status) {}
}
