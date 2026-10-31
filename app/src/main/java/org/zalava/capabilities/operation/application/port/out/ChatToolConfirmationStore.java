package org.zalava.capabilities.operation.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation.Choice;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.ToolKey;
import org.zalava.identity.accounts.domain.AccountRole;

public interface ChatToolConfirmationStore {
  List<ToolKey> savedTools();

  Optional<Boolean> role(AccountRole role, ToolKey key);

  Optional<Boolean> user(String accountId, ToolKey key);

  void setRole(String administratorId, AccountRole role, ToolKey key, Choice choice, Instant at);

  void setUser(String administratorId, String accountId, ToolKey key, Choice choice, Instant at);
}
