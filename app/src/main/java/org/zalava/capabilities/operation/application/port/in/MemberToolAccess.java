package org.zalava.capabilities.operation.application.port.in;

import java.time.Instant;
import java.util.List;

/** Persisted, exact per-account grants. Runtime safety and confirmation remain independent. */
public interface MemberToolAccess {
  List<ToolChoice> availableTools(String administratorId);

  record ToolChoice(ToolKey key, String providerName, String description, boolean eligible) {}

  boolean allowed(String accountId, ToolKey key);

  List<Decision> decisions(String administratorId, String memberId);

  void decide(String administratorId, String memberId, ToolKey key, boolean allowed);

  List<Decision> defaults(String administratorId);

  void defaultAccess(String administratorId, ToolKey key, boolean membersAllowed);

  void inherit(String administratorId, String memberId, ToolKey key);

  Effective effective(String administratorId, String memberId, ToolKey key);

  record Effective(boolean allowed, String source, String override, boolean membersAllowed) {}

  record ToolKey(String moduleId, String providerId, String toolName) {
    public ToolKey {
      for (String value : List.of(moduleId, providerId, toolName)) {
        if (value.isBlank()
            || value.length() > 160
            || value.chars().anyMatch(Character::isISOControl))
          throw new IllegalArgumentException("Invalid tool identity");
      }
    }
  }

  record Decision(ToolKey key, boolean allowed, String changedBy, Instant changedAt) {}
}
