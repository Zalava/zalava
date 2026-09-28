package org.zalava.chat.domain;

import java.util.List;
import org.zalava.tasks.domain.ActorTaskReference;

/** Product chat response with only opaque actor-owned job references. */
public record ActorChatTurn(String text, List<ActorTaskReference> taskReferences) {
  public ActorChatTurn {
    taskReferences = List.copyOf(taskReferences);
  }
}
