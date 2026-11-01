package org.zalava.capabilities.operation.application.port.in;

import java.util.List;
import java.util.Optional;
import org.zalava.api.InvocationContext;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.ToolKey;
import org.zalava.identity.accounts.domain.AccountRole;

/** Confirmation policy is independent of permission to discover or invoke a tool. */
public interface ChatToolConfirmation {
  enum Choice {
    INHERIT,
    REQUIRE,
    AUTO
  }

  record Tool(
      ToolKey key,
      String providerName,
      String description,
      boolean available,
      Boolean sideEffecting,
      String hostAccessNote) {}

  record Effective(Choice override, Choice roleDefault, Choice effective, String source) {}

  List<Tool> tools(String administratorId);

  Effective effective(String administratorId, String accountId, ToolKey key);

  Choice roleDefault(String administratorId, AccountRole role, ToolKey key);

  void setRole(String administratorId, AccountRole role, ToolKey key, Choice choice);

  void setUser(String administratorId, String accountId, ToolKey key, Choice choice);

  /** Revalidate the current account before consuming a read approval. */
  default boolean mayConsumeReadApproval(InvocationContext context) {
    return false;
  }

  /** Empty means preserve the previous approval policy. Present true requires a confirmation. */
  Optional<Boolean> requireFor(
      InvocationContext context, ZalavaProvider provider, ZalavaToolDescriptor tool);
}
