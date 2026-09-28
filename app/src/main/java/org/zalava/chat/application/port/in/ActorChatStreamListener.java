package org.zalava.chat.application.port.in;

import java.util.List;
import org.zalava.tasks.domain.ActorTaskReference;

/**
 * Receives progressive output of an actor chat turn. Callbacks run on the thread executing the turn
 * (the WebSocket worker thread); implementations must not block.
 */
public interface ActorChatStreamListener {
  /** An incremental, boundary-safe text batch of the assistant response. */
  void onDelta(String text);

  /** The turn finished; the full text and any job references created during the turn. */
  void onComplete(String fullText, List<ActorTaskReference> jobReferences);

  /** The turn failed; no completion callback follows. */
  void onError(RuntimeException failure);
}
