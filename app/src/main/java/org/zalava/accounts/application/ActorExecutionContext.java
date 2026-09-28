package org.zalava.accounts.application;

import java.util.Optional;
import java.util.function.Supplier;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;

/** Trusted request/task context used to propagate an authenticated actor to provider boundaries. */
public final class ActorExecutionContext {
  private final ThreadLocal<Principal> current = new ThreadLocal<>();

  public <T> T call(Actor actor, AccountRole role, Supplier<T> operation) {
    Principal previous = current.get();
    current.set(new Principal(actor, role));
    try {
      return operation.get();
    } finally {
      if (previous == null) current.remove();
      else current.set(previous);
    }
  }

  public Optional<Principal> currentPrincipal() {
    return Optional.ofNullable(current.get());
  }

  public record Principal(Actor actor, AccountRole role) {}
}
