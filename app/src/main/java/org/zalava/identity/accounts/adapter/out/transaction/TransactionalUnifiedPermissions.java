package org.zalava.identity.accounts.adapter.out.transaction;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess;
import org.zalava.identity.accounts.application.UnifiedPermissions;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.modules.web.application.port.in.ModulePageAccess;

/** Makes a permissions batch atomic across the existing policy stores. */
public class TransactionalUnifiedPermissions extends UnifiedPermissions {
  public TransactionalUnifiedPermissions(
      MemberToolAccess tools,
      ChatToolConfirmation confirmation,
      ModulePageAccess pages,
      AccountLifecycle accounts) {
    super(tools, confirmation, pages, accounts);
  }

  @Override
  @Transactional
  public void saveBatch(String admin, String subject, List<Change> changes) {
    super.saveBatch(admin, subject, changes);
  }
}
