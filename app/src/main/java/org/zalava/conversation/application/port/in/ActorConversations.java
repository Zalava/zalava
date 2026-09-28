package org.zalava.conversation.application.port.in;

import java.util.List;
import org.zalava.accounts.domain.Actor;
import org.zalava.conversation.domain.ConversationMessage;
import org.zalava.conversation.domain.ConversationReference;

/** Actor-bound private conversation access. */
public interface ActorConversations {
  List<ConversationReference> findReferences(Actor actor);

  List<ConversationMessage> findByReference(Actor actor, ConversationReference reference);

  void appendAll(Actor actor, ConversationReference reference, List<ConversationMessage> messages);

  void saveAll(Actor actor, ConversationReference reference, List<ConversationMessage> messages);

  void delete(Actor actor, ConversationReference reference);
}
