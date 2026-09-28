package org.zalava.chat.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.zalava.accounts.application.port.in.AccountLifecycle;
import org.zalava.accounts.domain.Account;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;
import org.zalava.chat.application.port.in.ActorChatQueries;
import org.zalava.chat.domain.ChatMessage;
import org.zalava.conversation.domain.ConversationReference;
import org.zalava.support.SecureSeaComponentTest;
import tools.jackson.databind.ObjectMapper;

/**
 * Full-context component test for the attachment authority. It drives the real {@link
 * UiChatWebSocketHandler} with the real owner-scoped attachment store and knowledge import
 * pipeline, proving ownership, bounds, replay and deletion boundaries the mock-based handler tests
 * cannot see.
 */
@SecureSeaComponentTest
@ResourceLock("secure-component-runtime")
class UiChatWebSocketAttachmentComponentTest {
  private static final AtomicInteger LOGINS = new AtomicInteger();
  private static final String CONTENT = Base64.getEncoder().encodeToString("hello".getBytes());

  @Autowired private UiChatWebSocketHandler handler;
  @Autowired private AccountLifecycle accounts;
  @Autowired private ActorChatQueries chatQueries;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void storesTaskOnlyAttachmentAndReplaysItForTheOwner() throws Exception {
    Account owner = member();
    WebSocketSession session = session(owner);

    handler.handleTextMessage(session, put("task-only", "notes.txt", "text/plain"));
    String attachmentId = attachmentId(payloads(session));

    assertThat(attachmentId).isNotBlank();
    assertThat(payloads(session)).contains("attachment.available", "task-only", "notes.txt");

    WebSocketSession reconnect = session(owner);
    handler.afterConnectionEstablished(reconnect);
    assertThat(payloads(reconnect)).contains(attachmentId).contains("notes.txt");
  }

  @Test
  void importsDurablyWithAnOwnerScopedSourceReference() throws Exception {
    WebSocketSession session = session(member());

    handler.handleTextMessage(session, put("knowledge-import", "report.txt", "text/plain"));

    assertThat(payloads(session))
        .contains("attachment.available")
        .contains("knowledge-import")
        .contains("sourceId");
  }

  @Test
  void deletesAnOwnedAttachmentAndConfirmsRemoval() throws Exception {
    Account owner = member();
    WebSocketSession session = session(owner);
    handler.handleTextMessage(session, put("task-only", "notes.txt", "text/plain"));
    String attachmentId = attachmentId(payloads(session));

    handler.handleTextMessage(session, delete(attachmentId));

    assertThat(payloads(session)).contains("attachment.removed").contains(attachmentId);
    WebSocketSession reconnect = session(owner);
    handler.afterConnectionEstablished(reconnect);
    assertThat(payloads(reconnect)).doesNotContain(attachmentId);
  }

  @Test
  void anotherActorCannotSeeOrDeleteAnOwnersAttachment() throws Exception {
    Account owner = member();
    WebSocketSession ownerSession = session(owner);
    handler.handleTextMessage(ownerSession, put("task-only", "notes.txt", "text/plain"));
    String attachmentId = attachmentId(payloads(ownerSession));

    WebSocketSession otherSession = session(member());
    handler.handleTextMessage(otherSession, delete(attachmentId));

    assertThat(payloads(otherSession)).contains("failure").doesNotContain("attachment.removed");

    WebSocketSession ownerReconnect = session(owner);
    handler.afterConnectionEstablished(ownerReconnect);
    assertThat(payloads(ownerReconnect)).contains(attachmentId);
  }

  @Test
  void rejectsUnsupportedTypesWithoutStoringAnything() throws Exception {
    WebSocketSession session = session(member());

    handler.handleTextMessage(session, put("task-only", "archive.zip", "application/zip"));

    assertThat(payloads(session)).contains("failure").doesNotContain("attachment.available");
  }

  @Test
  void includesBoundedAttachmentManifestInTheOwnedChatTurn() throws Exception {
    Account owner = member();
    WebSocketSession session = session(owner);
    handler.handleTextMessage(session, put("task-only", "notes.txt", "text/plain"));
    String attachmentId = attachmentId(payloads(session));

    handler.handleTextMessage(
        session,
        new TextMessage(
            objectMapper.writeValueAsString(
                Map.of("protocol", "sea.ui/v1", "type", "chat.create"))));
    String conversationId = conversationId(payloads(session));

    handler.handleTextMessage(session, sendChat(conversationId, attachmentId));

    String history =
        chatQueries
            .history(new Actor(owner.id()), new ConversationReference(conversationId))
            .stream()
            .map(ChatMessage::text)
            .reduce("", String::concat);
    assertThat(history).contains("Attached file: notes.txt").contains("text/plain");
  }

  private TextMessage put(String intent, String name, String contentType) throws Exception {
    return new TextMessage(
        objectMapper.writeValueAsString(
            Map.of(
                "protocol", "sea.ui/v1",
                "type", "attachment.put",
                "intent", intent,
                "name", name,
                "contentType", contentType,
                "content", CONTENT)));
  }

  private TextMessage delete(String attachmentId) throws Exception {
    return new TextMessage(
        objectMapper.writeValueAsString(
            Map.of(
                "protocol", "sea.ui/v1",
                "type", "attachment.delete",
                "attachmentId", attachmentId)));
  }

  private TextMessage sendChat(String conversationId, String attachmentId) throws Exception {
    return new TextMessage(
        objectMapper.writeValueAsString(
            Map.of(
                "protocol", "sea.ui/v1",
                "type", "chat.send",
                "conversationId", conversationId,
                "message", "summarize the attachment",
                "attachmentIds", List.of(attachmentId))));
  }

  private static String attachmentId(String payloads) {
    return firstString(payloads, "\"attachmentId\":\"");
  }

  private static String conversationId(String payloads) {
    return firstString(payloads, "\"conversationId\":\"");
  }

  private static String firstString(String payloads, String marker) {
    int index = payloads.indexOf(marker);
    if (index < 0) return null;
    int start = index + marker.length();
    int end = payloads.indexOf('"', start);
    return payloads.substring(start, end);
  }

  private static WebSocketSession session(Account account) {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getPrincipal()).thenReturn((Principal) () -> account.loginName());
    when(session.isOpen()).thenReturn(true);
    return session;
  }

  private static String payloads(WebSocketSession session) {
    ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
    try {
      verify(session, atLeastOnce()).sendMessage(sent.capture());
    } catch (java.io.IOException exception) {
      throw new IllegalStateException(exception);
    }
    return sent.getAllValues().stream().map(TextMessage::getPayload).reduce("", String::concat);
  }

  private Account member() {
    String login = "attachment-member-" + LOGINS.incrementAndGet();
    Account account = accounts.create(login, "TemporaryPassword-123", AccountRole.MEMBER);
    accounts.changePassword(account.id(), "TemporaryPassword-123", "PermanentPassword-123");
    return accounts.findByLoginName(login).orElseThrow();
  }
}
