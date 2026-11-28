package org.zalava.capabilities.operation.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** The actor, provider and exact invocation to which an approval is bound. */
public record ApprovalBinding(
    String actorId,
    String moduleId,
    Map<String, String> providerScope,
    String role,
    String conversationId,
    Map<String, Object> arguments) {
  public ApprovalBinding {
    providerScope = Collections.unmodifiableMap(new LinkedHashMap<>(providerScope));
    arguments = Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
  }

  public boolean matches(ApprovalBinding invocation) {
    return invocation != null
        && actorId != null
        && moduleId != null
        && Objects.equals(actorId, invocation.actorId)
        && Objects.equals(moduleId, invocation.moduleId)
        && providerScope.equals(invocation.providerScope)
        && Objects.equals(role, invocation.role)
        && Objects.equals(conversationId, invocation.conversationId)
        && arguments.equals(invocation.arguments);
  }
}
