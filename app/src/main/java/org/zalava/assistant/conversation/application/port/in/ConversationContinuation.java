package org.zalava.assistant.conversation.application.port.in;

import org.zalava.assistant.conversation.domain.ConversationOrigin;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.identity.accounts.domain.Actor;

/** Owner and destination authorization is enforced here, never by the browser. */
public interface ConversationContinuation {
  ConversationReference channelConversation(Actor actor, ConversationOrigin origin);

  ConversationOrigin origin(Actor actor, ConversationReference reference);

  boolean canContinue(Actor actor, ConversationReference reference);

  ConversationReference continueOnWeb(
      Actor actor, ConversationReference source, String destination);

  void requireWeb(Actor actor, ConversationReference reference);
}
