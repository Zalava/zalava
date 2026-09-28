package org.zalava.conversation.domain;

import java.util.Objects;
import java.util.UUID;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;

/**
 * Private internal conversation identity passed to model adapters. It is derived only from an
 * authenticated actor and an opaque public reference; it is never supplied by a browser client.
 */
public record ActorConversationId(Actor actor, ConversationReference reference) {
  private static final String SEPARATOR = ":";

  public ActorConversationId {
    Objects.requireNonNull(actor, "actor");
    Objects.requireNonNull(reference, "reference");
  }

  public String value() {
    return actor.accountId() + SEPARATOR + reference.value();
  }

  public static ActorConversationId parse(String value) {
    if (value == null) throw new IllegalArgumentException("Invalid actor conversation identity");
    int separator = value.indexOf(SEPARATOR);
    if (separator <= 0 || separator != value.lastIndexOf(SEPARATOR)) {
      throw new IllegalArgumentException("Invalid actor conversation identity");
    }
    return new ActorConversationId(
        new Actor(new AccountId(UUID.fromString(value.substring(0, separator)))),
        new ConversationReference(value.substring(separator + 1)));
  }
}
