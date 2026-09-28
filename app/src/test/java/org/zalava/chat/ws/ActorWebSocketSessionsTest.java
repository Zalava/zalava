package org.zalava.chat.ws;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;

class ActorWebSocketSessionsTest {
  @Test
  void sendsOnlyToTheOwningActorsSession() throws Exception {
    Actor owner = new Actor(AccountId.newId());
    Actor other = new Actor(AccountId.newId());
    WebSocketSession ownerSession = mock(WebSocketSession.class);
    WebSocketSession otherSession = mock(WebSocketSession.class);
    when(ownerSession.isOpen()).thenReturn(true);
    when(otherSession.isOpen()).thenReturn(true);
    var sessions = new ActorWebSocketSessions();
    sessions.connect(owner, ownerSession);
    sessions.connect(other, otherSession);

    sessions.send(owner, "private delivery");

    verify(ownerSession)
        .sendMessage(new org.springframework.web.socket.TextMessage("private delivery"));
    verify(otherSession, never()).sendMessage(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void deliversToEveryOpenSessionOwnedByTheActor() throws Exception {
    Actor owner = new Actor(AccountId.newId());
    WebSocketSession first = mock(WebSocketSession.class);
    WebSocketSession second = mock(WebSocketSession.class);
    when(first.isOpen()).thenReturn(true);
    when(second.isOpen()).thenReturn(true);
    var sessions = new ActorWebSocketSessions();
    sessions.connect(owner, first);
    sessions.connect(owner, second);

    sessions.send(owner, "owner update");

    var message = new org.springframework.web.socket.TextMessage("owner update");
    verify(first).sendMessage(message);
    verify(second).sendMessage(message);
  }
}
