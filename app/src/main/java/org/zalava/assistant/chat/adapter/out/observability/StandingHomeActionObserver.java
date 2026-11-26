package org.zalava.assistant.chat.adapter.out.observability;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.zalava.assistant.chat.application.port.out.StandingHomeActionEvidenceStore;
import org.zalava.assistant.chat.application.port.out.StandingHomeActionEvidenceStore.Evidence;
import org.zalava.assistant.conversation.domain.ActorConversationId;
import org.zalava.capabilities.operation.application.port.out.ToolInvocationObservation;
import org.zalava.capabilities.operation.application.port.out.ToolInvocationObserver;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.identity.channels.application.InvocationChannelContext;

/** Records only completed, authenticated Chat executions under a scoped tool grant. */
@Component
public final class StandingHomeActionObserver implements ToolInvocationObserver {
  private static final Logger LOG = LoggerFactory.getLogger(StandingHomeActionObserver.class);
  private final StandingHomeActionEvidenceStore store;

  public StandingHomeActionObserver(StandingHomeActionEvidenceStore store) {
    this.store = store;
  }

  @Override
  public void observe(ToolInvocationObservation observation) {
    if (!"home-assistant".equals(observation.providerId())
        || !"home_assistant_execute_action".equals(observation.toolName())
        || !observation.confirmed()
        || (observation.result() == null
            && (observation.errorType() == null
                || !Set.of("execution", "validation", "unsupported")
                    .contains(observation.errorType())))
        || !"tool".equals(observation.attributes().get("approvalPolicy"))
        || !"zalava-module-home-assistant"
            .equals(observation.attributes().get(ToolInvocationObservation.HOME_AUDIT_MODULE))
        || !"web".equals(observation.attributes().get("permissionPolicyChannel"))) return;
    try {
      Actor actor = new Actor(new AccountId(UUID.fromString(observation.actorId())));
      String conversation = observation.attributes().get("conversationId");
      if (!ActorConversationId.parse(conversation).actor().equals(actor)) return;
      String runId = observation.attributes().get(InvocationChannelContext.RUN_ID);
      Object domain = observation.arguments().get("domain");
      Object action = observation.arguments().get("action");
      Object entity = observation.arguments().get("entityId");
      if (!(domain instanceof String domainValue)
          || !(action instanceof String actionValue)
          || !(entity instanceof String entityValue)) return;
      store.record(
          actor,
          new Evidence(
              Instant.now().toString(),
              conversation,
              runId,
              observation.attributes().get(ToolInvocationObservation.HOME_AUDIT_EXECUTION),
              domainValue,
              actionValue,
              entityValue,
              observation.success(),
              observation.durationMillis()));
    } catch (RuntimeException failure) {
      // Execution has already completed; an audit failure must not invite a retry.
      LOG.warn("Could not retain Home Assistant standing-grant evidence");
    }
  }
}
