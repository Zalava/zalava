package org.zalava.assistant.conversation.application.port.out;

import java.util.Optional;
import org.zalava.assistant.conversation.domain.ConversationOrigin;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.identity.accounts.domain.Actor;

public interface ConversationOriginStore {
  Optional<ConversationOrigin> origin(Actor actor, ConversationReference reference);

  void saveOrigin(Actor actor, ConversationReference reference, ConversationOrigin origin);
}
