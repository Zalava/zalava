package org.zalava.assistant.channels.runtime.application.port.out;

import org.zalava.assistant.conversation.domain.ConversationOrigin;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.identity.accounts.domain.Actor;

@FunctionalInterface
public interface ChannelConversationRouting {
  ConversationReference resolve(Actor actor, ConversationOrigin origin);
}
