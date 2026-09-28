package org.zalava.chat.application.port.in;

import org.zalava.accounts.domain.Actor;
import org.zalava.chat.domain.ActorChatTurn;
import org.zalava.conversation.domain.ConversationReference;

/** Product chat commands must receive an authenticated actor, never browser-selected ownership. */
public interface ActorChatCommands {
  ActorChatTurn chat(Actor actor, ConversationReference conversation, String message);

  /**
   * Streaming variant of {@link #chat}: boundary-safe partial text is forwarded to the listener as
   * it is produced and the returned turn carries the full text and any created job references. The
   * default delegates to {@link #chat} for callers that have not migrated.
   */
  default ActorChatTurn streamChat(
      Actor actor,
      ConversationReference conversation,
      String message,
      org.zalava.chat.application.port.in.ActorChatStreamListener listener) {
    ActorChatTurn turn = chat(actor, conversation, message);
    listener.onDelta(turn.text());
    listener.onComplete(turn.text(), turn.taskReferences());
    return turn;
  }

  ConversationReference createWebConversation(Actor actor);
}
