package org.zalava.assistant.chat.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.support.SecureZalavaComponentTest;

/**
 * Full-context component test for the actor-scoped web chat bootstrap. It drives the real {@link
 * ChatWebSocketHandler} with the real actor chat use cases, which create bare-UUID conversation
 * references, so it catches presentation bugs the mock-based handler tests cannot see.
 */
@SecureZalavaComponentTest
@ResourceLock("secure-component-runtime")
class ChatWebSocketActorComponentTest {
  private static final AtomicInteger LOGINS = new AtomicInteger();

  @Autowired AccountLifecycle accounts;
  @Autowired ChatWebSocketHandler handler;

  @Test
  void firstConnectionOffersAWritableInputForTheActorConversation() throws Exception {
    Account member = member();

    String payload = connectAs(member);

    assertThat(payload)
        .contains("chat-input-area")
        .contains("chat-form")
        .contains("Message Zalava...")
        .doesNotContain("read-only view");
    assertThat(payload).contains("channel-selector").contains("Web Chat");
  }

  private String connectAs(Account member) throws Exception {
    WebSocketSession session = authenticatedSession(member);
    try {
      handler.afterConnectionEstablished(session);
      return lastSentPayload(session);
    } finally {
      handler.afterConnectionClosed(session, CloseStatus.NORMAL);
    }
  }

  private static WebSocketSession authenticatedSession(Account member) {
    WebSocketSession session = mock(WebSocketSession.class);
    org.mockito.Mockito.when(session.getPrincipal()).thenReturn(() -> member.loginName());
    org.mockito.Mockito.when(session.isOpen()).thenReturn(true);
    return session;
  }

  private static String lastSentPayload(WebSocketSession session) throws IOException {
    ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
    verify(session, atLeastOnce()).sendMessage(messages.capture());
    return messages.getValue().getPayload();
  }

  private Account member() {
    String login = "chat-member-" + LOGINS.incrementAndGet();
    Account account = accounts.create(login, "TemporaryPassword-123", AccountRole.MEMBER);
    accounts.changePassword(account.id(), "TemporaryPassword-123", "PermanentPassword-123");
    return accounts.findByLoginName(login).orElseThrow();
  }
}
