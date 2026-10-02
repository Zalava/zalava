package org.zalava.assistant.chat.application.port.in;

import java.util.List;
import org.zalava.assistant.chat.domain.ChatMessage;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.identity.accounts.domain.Actor;

/** Actor-scoped product conversation lookup. */
public interface ActorChatQueries {
  List<ConversationReference> conversations(Actor actor);

  List<ChatMessage> history(Actor actor, ConversationReference conversation);
}
