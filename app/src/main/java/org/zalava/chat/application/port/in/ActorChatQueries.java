package org.zalava.chat.application.port.in;

import java.util.List;
import org.zalava.accounts.domain.Actor;
import org.zalava.chat.domain.ChatMessage;
import org.zalava.conversation.domain.ConversationReference;

/** Actor-scoped product conversation lookup. */
public interface ActorChatQueries {
  List<ConversationReference> conversations(Actor actor);

  List<ChatMessage> history(Actor actor, ConversationReference conversation);
}
