package org.zalava.tasks.adapter.out.channel;

import java.io.IOException;
import org.springframework.stereotype.Component;
import org.zalava.accounts.domain.Actor;
import org.zalava.chat.ChatHtml;
import org.zalava.chat.Htmx;
import org.zalava.chat.ws.ActorWebSocketSessions;
import org.zalava.tasks.application.port.out.ActorTaskNotifier;
import org.zalava.tasks.domain.Task;

/** Delivers actor-owned task results only to that actor's active product socket. */
@Component
public final class ActorWebSocketTaskNotifier implements ActorTaskNotifier {
  private final ActorWebSocketSessions sessions;

  public ActorWebSocketTaskNotifier(ActorWebSocketSessions sessions) {
    this.sessions = sessions;
  }

  @Override
  public void notify(Actor actor, String taskName, Task.Status status, String feedback) {
    if (status != Task.Status.completed && status != Task.Status.awaiting_human_input) return;
    String label = status == Task.Status.completed ? "completed" : "is waiting for your input";
    try {
      sessions.send(
          actor,
          Htmx.oobAppend(
              "chat-messages",
              ChatHtml.agentBubble("📋 Task '%s' %s:\n%s".formatted(taskName, label, feedback))));
    } catch (IOException ignored) {
      // Socket delivery is best-effort; owned task state remains authoritative.
    }
  }
}
