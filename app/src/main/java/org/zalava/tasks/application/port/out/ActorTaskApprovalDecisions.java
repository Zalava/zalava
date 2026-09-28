package org.zalava.tasks.application.port.out;

import java.util.List;
import java.util.Map;
import org.zalava.accounts.domain.Actor;
import org.zalava.tasks.domain.ActorTaskReference;

/** Approval state visible only through an actor/task ownership pair. */
public interface ActorTaskApprovalDecisions {
  boolean hasPending(Actor actor, ActorTaskReference taskReference);

  List<PendingApproval> pendingFor(Actor actor, ActorTaskReference taskReference);

  List<Decision> unconsumedFor(Actor actor, ActorTaskReference taskReference);

  record Decision(String decision, String providerId, String toolName, String argumentsJson) {}

  record PendingApproval(
      String requestId,
      String providerId,
      String toolName,
      String prompt,
      String effect,
      String argumentsPreview,
      Map<String, String> scope,
      List<String> policyTags) {
    public PendingApproval {
      scope = scope == null ? Map.of() : Map.copyOf(scope);
      policyTags = policyTags == null ? List.of() : List.copyOf(policyTags);
    }
  }
}
