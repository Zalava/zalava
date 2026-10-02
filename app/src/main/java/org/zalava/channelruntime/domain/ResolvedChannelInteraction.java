package org.zalava.channelruntime.domain;

import java.util.Objects;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.channels.IncomingInteraction;
import org.zalava.identity.accounts.domain.Actor;

/** An interaction Core has authenticated and associated with an actor-owned conversation. */
public record ResolvedChannelInteraction(
    Actor actor, ConversationReference conversation, IncomingInteraction interaction) {
  public ResolvedChannelInteraction {
    actor = Objects.requireNonNull(actor, "actor must not be null");
    conversation = Objects.requireNonNull(conversation, "conversation must not be null");
    interaction = Objects.requireNonNull(interaction, "interaction must not be null");
  }
}
