package org.zalava.assistant.chat.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.zalava.assistant.conversation.domain.ActorConversationId;
import org.zalava.identity.accounts.domain.Actor;

/** Bounded evidence for Home Assistant actions executed under an existing grant. */
public interface StandingHomeActionEvidenceStore {
  void record(Actor actor, Evidence evidence);

  List<Evidence> recent(Actor actor, String conversationId);

  record Evidence(
      String timestamp,
      String conversationId,
      String runId,
      String executionId,
      String domain,
      String action,
      String entityId,
      boolean success,
      long durationMillis) {
    public Evidence {
      if (Instant.parse(timestamp).isBefore(Instant.EPOCH))
        throw new IllegalArgumentException("Invalid Home Assistant evidence timestamp");
      ActorConversationId.parse(conversationId);
      UUID.fromString(runId);
      UUID.fromString(executionId);
      if (domain == null
          || !domain.matches("[a-z0-9_]{1,128}")
          || action == null
          || !action.matches("[a-z0-9_]{1,128}")
          || entityId == null
          || !entityId.matches("[a-z0-9_]{1,128}\\.[a-z0-9_]{1,128}")
          || durationMillis < 0) {
        throw new IllegalArgumentException("Invalid Home Assistant execution evidence");
      }
    }
  }
}
