package org.zalava.tasks.clarification.application.port.in;

import java.util.List;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.clarification.domain.ClarificationRequest;

/** Actor-owned commands and queries for persisted clarification requests. */
public interface ClarificationResponses {
  ClarificationRequest answer(
      Actor actor, String requestId, List<ClarificationRequest.Answer> answers);

  ClarificationRequest cancel(Actor actor, String requestId);

  List<ClarificationRequest> pending(Actor actor);

  ClarificationRequest get(Actor actor, String requestId);
}
