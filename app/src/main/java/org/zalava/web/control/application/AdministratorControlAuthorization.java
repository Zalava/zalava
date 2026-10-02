package org.zalava.web.control.application;

import java.util.List;
import java.util.function.Supplier;
import org.zalava.identity.accounts.domain.AccountRole;

/** Application port for administrator-only, instance-wide control operations. */
public interface AdministratorControlAuthorization {
  <T> T call(String target, Supplier<T> operation);

  void run(String target, Runnable operation);

  List<Entry> recentEntries();

  record Entry(String timestamp, String actorId, AccountRole role, String target) {}
}
