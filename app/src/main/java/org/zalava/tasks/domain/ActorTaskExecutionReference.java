package org.zalava.tasks.domain;

import java.util.Objects;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;

/** Scheduler-safe task identity that carries only opaque owner and task UUIDs. */
public record ActorTaskExecutionReference(Actor actor, ActorTaskReference taskReference) {
  private static final String SEPARATOR = ":";

  public ActorTaskExecutionReference {
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(taskReference, "taskReference");
  }

  public String encode() {
    return actor.accountId() + SEPARATOR + taskReference.value();
  }

  public static ActorTaskExecutionReference parse(String value) {
    if (value == null) throw new IllegalArgumentException("Missing actor task execution reference");
    String[] parts = value.split(SEPARATOR, -1);
    if (parts.length != 2)
      throw new IllegalArgumentException("Invalid actor task execution reference");
    try {
      return new ActorTaskExecutionReference(
          new Actor(new AccountId(java.util.UUID.fromString(parts[0]))),
          new ActorTaskReference(parts[1]));
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Invalid actor task execution reference", exception);
    }
  }
}
