package org.zalava.assistant.chat.application.port.out;

import java.util.List;
import java.util.Set;
import org.zalava.assistant.chat.application.ConversationEvidenceQueries.Tool;
import org.zalava.identity.accounts.domain.Actor;

/** Provider-specific evidence translated into the conversation's generic representation. */
public interface ConversationEvidenceSupplement {
  List<Tool> retainedApprovals(Actor actor, String conversationId, Set<String> observedApprovalIds);

  List<Execution> executions(Actor actor, String conversationId);

  record Execution(String executionId, String outcome, Tool tool) {}
}
