package org.zalava.assistant.chat.application.port.in;

import java.util.List;
import org.zalava.assistant.chat.domain.ChatMessage;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.assistant.conversation.domain.ConversationSummary;
import org.zalava.identity.accounts.domain.Actor;

/** Actor-scoped product conversation lookup. */
public interface ActorChatQueries {
  List<ConversationReference> conversations(Actor actor);

  default List<ConversationSummary> summaries(Actor actor) {
    return conversations(actor).stream()
        .map(
            reference -> new ConversationSummary(reference.value(), "New conversation", null, null))
        .toList();
  }

  List<ChatMessage> history(Actor actor, ConversationReference conversation);
}
