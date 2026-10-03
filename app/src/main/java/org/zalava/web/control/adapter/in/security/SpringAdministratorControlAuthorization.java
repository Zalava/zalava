package org.zalava.web.control.adapter.in.security;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.security.AuthenticatedActorResolver;
import org.zalava.web.control.application.AdministratorControlAuthorization;

/** Spring Security adapter for the administrator control application port. */
@Component
public final class SpringAdministratorControlAuthorization
    implements AdministratorControlAuthorization {
  private static final int MAX_AUDIT_ENTRIES = 100;

  private final ActorExecutionContext actorExecution;
  private final AuthenticatedActorResolver actors;
  private final boolean securityEnabled;
  private final ArrayDeque<Entry> entries = new ArrayDeque<>();

  public SpringAdministratorControlAuthorization(
      ActorExecutionContext actorExecution,
      AuthenticatedActorResolver actors,
      @Value("${zalava.accounts.security-enabled:true}") boolean securityEnabled) {
    this.actorExecution = actorExecution;
    this.actors = actors;
    this.securityEnabled = securityEnabled;
  }

  @Override
  public <T> T call(String target, Supplier<T> operation) {
    Identity identity = requireAdministrator();
    T result = operation.get();
    record(identity, target);
    return result;
  }

  @Override
  public void run(String target, Runnable operation) {
    call(
        target,
        () -> {
          operation.run();
          return null;
        });
  }

  @Override
  public synchronized List<Entry> recentEntries() {
    return List.copyOf(new ArrayList<>(entries));
  }

  private Identity requireAdministrator() {
    var current = actorExecution.currentPrincipal();
    if (current.isPresent()) {
      if (current.get().role() != AccountRole.ADMIN) {
        throw new AccessDeniedException("Administrator access is required");
      }
      return new Identity(current.get().actor().accountId().toString(), current.get().role());
    }
    if (!securityEnabled) return new Identity("legacy-operator", AccountRole.ADMIN);

    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (actors.role(authentication) != AccountRole.ADMIN) {
      throw new AccessDeniedException("Administrator access is required");
    }
    return new Identity(actors.actor(authentication).accountId().toString(), AccountRole.ADMIN);
  }

  private synchronized void record(Identity identity, String target) {
    entries.addFirst(
        new Entry(Instant.now().toString(), identity.actorId(), identity.role(), target));
    while (entries.size() > MAX_AUDIT_ENTRIES) entries.removeLast();
  }

  private record Identity(String actorId, AccountRole role) {}
}
