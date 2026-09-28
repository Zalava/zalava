package org.zalava.chat.ws;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.zalava.accounts.domain.Actor;

/** Active product WebSocket sessions, partitioned by the authenticated SEA actor. */
@Component
public final class ActorWebSocketSessions {
  private final ConcurrentMap<Actor, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

  public void connect(Actor actor, WebSocketSession session) {
    sessions.computeIfAbsent(actor, ignored -> ConcurrentHashMap.newKeySet()).add(session);
  }

  public void disconnect(Actor actor, WebSocketSession session) {
    Set<WebSocketSession> actorSessions = sessions.get(actor);
    if (actorSessions == null) return;
    actorSessions.remove(session);
    if (actorSessions.isEmpty()) sessions.remove(actor, actorSessions);
  }

  public void disconnect(WebSocketSession session) {
    sessions.forEach((actor, ignored) -> disconnect(actor, session));
  }

  public void send(Actor actor, String text) throws IOException {
    Set<WebSocketSession> actorSessions = sessions.get(actor);
    if (actorSessions == null) return;
    IOException failure = null;
    for (WebSocketSession session : actorSessions) {
      if (!session.isOpen()) continue;
      try {
        session.sendMessage(new TextMessage(text));
      } catch (IOException exception) {
        failure = exception;
      }
    }
    if (failure != null) throw failure;
  }
}
