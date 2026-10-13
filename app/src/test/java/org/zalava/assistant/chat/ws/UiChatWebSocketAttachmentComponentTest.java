package org.zalava.assistant.chat.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.security.Principal;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.zalava.assistant.chat.application.port.in.ActorChatQueries;
import org.zalava.assistant.chat.attachment.application.ChatAttachments;
import org.zalava.assistant.chat.attachment.domain.ChatAttachmentIntent;
import org.zalava.assistant.chat.domain.ChatMessage;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.application.KnowledgeSourceLifecycle;
import org.zalava.knowledge.application.port.out.KnowledgeSourceStore;
import org.zalava.support.SecureZalavaComponentTest;
import tools.jackson.databind.ObjectMapper;

/**
 * Full-context component test for the attachment authority. It drives the real {@link
 * UiChatWebSocketHandler} with the real owner-scoped attachment store and knowledge import
 * pipeline, proving ownership, bounds, replay and deletion boundaries the mock-based handler tests
 * cannot see.
 */
@SecureZalavaComponentTest
@TestPropertySource(properties = {"zalava.test.chat-provider=openai"})
@ResourceLock("secure-component-runtime")
class UiChatWebSocketAttachmentComponentTest {
  private static final AtomicInteger LOGINS = new AtomicInteger();
  private static final String CONTENT = Base64.getEncoder().encodeToString("hello".getBytes());

  @Autowired private UiChatWebSocketHandler handler;
  @Autowired private AccountLifecycle accounts;
  @Autowired private ActorChatQueries chatQueries;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private KnowledgeSourceStore sources;
  @Autowired private KnowledgeSourceLifecycle lifecycle;
  @Autowired private ChatAttachments attachments;

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
  void missingExtractorDoesNotPersistAMetadataManifestAsUserText() throws Exception {
    Account owner = member();
    WebSocketSession session = session(owner);
    handler.handleTextMessage(session, put("task-only", "notes.txt", "text/plain"));
    String attachmentId = attachmentId(payloads(session));

    handler.handleTextMessage(
        session,
        new TextMessage(
            objectMapper.writeValueAsString(
                Map.of("protocol", "zalava.ui/v1", "type", "chat.create"))));
    String conversationId = conversationId(payloads(session));

    handler.handleTextMessage(session, sendChat(conversationId, attachmentId));

    String history =
        chatQueries
            .history(new Actor(owner.id()), new ConversationReference(conversationId))
            .stream()
            .map(ChatMessage::text)
            .reduce("", String::concat);
    assertThat(history).isEmpty();
    assertThat(payloads(session))
        .contains("document-processing module")
        .doesNotContain("chat.completed");
  }

  @Test
  void savesAnAlreadyUploadedFileWithoutUploadingAgainAndRepeatedClicksReuseItsSource()
      throws Exception {
    Account owner = member();
    Actor actor = new Actor(owner.id());
    WebSocketSession session = session(owner);
    handler.handleTextMessage(session, put("task-only", "saved-once.txt", "text/plain"));
    String id = attachmentId(payloads(session));
    TextMessage save =
        new TextMessage(
            objectMapper.writeValueAsString(
                Map.of(
                    "protocol", "zalava.ui/v1", "type", "attachment.import", "attachmentId", id)));
    handler.handleTextMessage(session, save);
    handler.handleTextMessage(session, save);
    assertThat(payloads(session)).contains("knowledge-import", "sourceId");
    var owned =
        sources.visibleTo(actor).stream()
            .filter(
                value ->
                    value.displayName().equals("saved-once.txt") && value.owner().equals(actor))
            .toList();
    assertThat(owned).hasSize(1);
    assertThat(attachments.read(actor, id)).isEqualTo("hello".getBytes());
    handler.handleTextMessage(session, delete(id));
    assertThat(sources.visibleTo(actor))
        .anyMatch(value -> value.id().equals(owned.getFirst().id()));
  }

  @Test
  void anotherActorCannotImportAnUploadedPrivateFile() throws Exception {
    Account owner = member();
    WebSocketSession ownerSession = session(owner);
    handler.handleTextMessage(ownerSession, put("task-only", "private-import.txt", "text/plain"));
    String id = attachmentId(payloads(ownerSession));
    Account outsider = member();
    WebSocketSession foreign = session(outsider);
    handler.handleTextMessage(
        foreign,
        new TextMessage(
            objectMapper.writeValueAsString(
                Map.of(
                    "protocol", "zalava.ui/v1", "type", "attachment.import", "attachmentId", id))));
    assertThat(payloads(foreign))
        .contains("failure")
        .doesNotContain("sourceId", "knowledge-import");
    assertThat(sources.visibleTo(new Actor(outsider.id())))
        .noneMatch(value -> value.displayName().equals("private-import.txt"));
  }

  @Test
  void deletingTheKnowledgeCopyMakesTheOriginalUploadAvailableToSaveAgain() {
    Account owner = member();
    Actor actor = new Actor(owner.id());
    var uploaded =
        attachments.put(
            actor, ChatAttachmentIntent.TASK_ONLY, "resave.txt", "text/plain", "hello".getBytes());
    var saved = attachments.importExisting(actor, uploaded.attachmentId());
    var source =
        sources.visibleTo(actor).stream()
            .filter(value -> value.id().value().toString().equals(saved.sourceId()))
            .findFirst()
            .orElseThrow();
    lifecycle.hardDelete(actor, source.id());
    assertThat(attachments.reference(actor, uploaded.attachmentId()).sourceId()).isNull();
    var again = attachments.importExisting(actor, uploaded.attachmentId());
    assertThat(again.sourceId()).isNotEqualTo(saved.sourceId());
    assertThat(sources.visibleTo(actor))
        .filteredOn(sourceValue -> sourceValue.owner().equals(actor))
        .hasSize(1);
  }

  private TextMessage put(String intent, String name, String contentType) throws Exception {
    return new TextMessage(
        objectMapper.writeValueAsString(
            Map.of(
                "protocol", "zalava.ui/v1",
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
                "protocol", "zalava.ui/v1",
                "type", "attachment.delete",
                "attachmentId", attachmentId)));
  }

  private TextMessage sendChat(String conversationId, String attachmentId) throws Exception {
    return new TextMessage(
        objectMapper.writeValueAsString(
            Map.of(
                "protocol", "zalava.ui/v1",
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
    } catch (IOException exception) {
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
