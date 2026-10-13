package org.zalava.assistant.chat.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.zalava.assistant.chat.application.port.in.ActorChatCommands;
import org.zalava.assistant.chat.application.port.in.ActorChatQueries;
import org.zalava.assistant.chat.domain.ChatMessage;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.modules.runtime.ZalavaRuntime;
import org.zalava.support.DocumentFixtureRuntime;
import org.zalava.support.SecureZalavaComponentTest;
import tools.jackson.databind.ObjectMapper;

@SecureZalavaComponentTest
@TestPropertySource(
    properties = {
      "zalava.test.chat-provider=openai",
      "zalava.test.context=attachment-reading",
      "agent.model-boundary.secret-values=attachment-secret-canary"
    })
class AttachmentReadingComponentTest {
  private static final String BODY = "The unique attachment fact is lavender-copper-1013.";
  private static final String QUESTION = "What unique fact is in the attachment?";

  @TestBean(name = "deterministicChatModel", methodName = "capturingModel", enforceOverride = true)
  ChatModel model;

  @TestBean(name = "zalavaRuntime", methodName = "artifactRuntime", enforceOverride = true)
  ZalavaRuntime runtime;

  @Autowired UiChatWebSocketHandler websocket;
  @Autowired ActorChatCommands commands;
  @Autowired ActorChatQueries queries;
  @Autowired AccountLifecycle accounts;
  @Autowired ObjectMapper json;
  private Account owner;
  private Actor actor;
  private ConversationReference conversation;

  static ChatModel capturingModel() {
    return new ChatContextComponentTest.CapturingModel();
  }

  static ZalavaRuntime artifactRuntime() throws Exception {
    return new DocumentFixtureRuntime();
  }

  @BeforeEach
  void prepare() {
    owner =
        accounts.create(
            "attachment-reading-" + UUID.randomUUID(),
            "AttachmentPassword-123",
            AccountRole.MEMBER);
    accounts.changePassword(owner.id(), "AttachmentPassword-123", "ChangedAttachmentPassword-123");
    actor = new Actor(owner.id());
    conversation = commands.createWebConversation(actor);
    fixture().available = true;
    captured().last.set(null);
  }

  @Test
  void anActiveExtractorReadsActualOwnedBytesIntoTheModel() throws Exception {
    WebSocketSession session = session(owner);
    String id = upload(session);
    websocket.handleTextMessage(session, send(id));
    assertThat(captured().last.get()).isNotNull();
    assertThat(captured().last.get().getInstructions().stream().map(m -> m.getText()).toList())
        .anySatisfy(text -> assertThat(text).contains(BODY, "notes.txt"));
    assertThat(payloads(session)).contains("chat.completed");
  }

  @Test
  void attachmentContextNeverReplacesAuthoredTextInPersistedHistory() throws Exception {
    WebSocketSession session = session(owner);
    websocket.handleTextMessage(session, send(upload(session)));
    assertThat(queries.history(actor, conversation))
        .filteredOn(m -> m.role() == ChatMessage.Role.USER)
        .extracting(ChatMessage::text)
        .containsExactly(QUESTION);
    assertThat(queries.history(actor, conversation))
        .filteredOn(m -> m.role() == ChatMessage.Role.USER)
        .extracting(ChatMessage::attachmentNames)
        .containsExactly(List.of("notes.txt"));
  }

  @Test
  void missingActiveExtractorExplainsThePrerequisiteWithoutPretendingToRead() throws Exception {
    fixture().available = false;
    WebSocketSession session = session(owner);
    websocket.handleTextMessage(session, send(upload(session)));
    assertThat(payloads(session))
        .contains("document-processing module")
        .doesNotContain("chat.completed");
    assertThat(captured().last.get()).isNull();
    assertThat(queries.history(actor, conversation)).isEmpty();
  }

  @Test
  void anotherActorsFileNeverReachesTheModel() throws Exception {
    WebSocketSession session = session(owner);
    String id = upload(session);
    Account other =
        accounts.create(
            "attachment-other-" + UUID.randomUUID(), "AttachmentPassword-123", AccountRole.MEMBER);
    accounts.changePassword(other.id(), "AttachmentPassword-123", "ChangedAttachmentPassword-123");
    ConversationReference otherConversation = commands.createWebConversation(new Actor(other.id()));
    WebSocketSession foreign = session(other);
    websocket.handleTextMessage(
        foreign,
        message(
            Map.of(
                "type",
                "chat.send",
                "conversationId",
                otherConversation.value(),
                "message",
                QUESTION,
                "attachmentIds",
                List.of(id))));
    assertThat(payloads(foreign)).contains("failure").doesNotContain(BODY, "chat.completed");
    assertThat(captured().last.get()).isNull();
  }

  @Test
  void extractedFileContentStillPassesThroughModelRedaction() throws Exception {
    WebSocketSession session = session(owner);
    websocket.handleTextMessage(
        session,
        send(upload(session, "The file contains attachment-secret-canary and a normal fact.")));
    assertThat(captured().last.get()).isNotNull();
    assertThat(captured().last.get().getInstructions().stream().map(m -> m.getText()).toList())
        .anySatisfy(
            text ->
                assertThat(text)
                    .contains("[REDACTED]", "normal fact")
                    .doesNotContain("attachment-secret-canary"));
    assertThat(queries.history(actor, conversation))
        .filteredOn(m -> m.role() == ChatMessage.Role.USER)
        .extracting(ChatMessage::text)
        .containsExactly(QUESTION);
  }

  @Test
  void oversizedExtractedOutputFailsBeforeModelOrHistoryWrite() throws Exception {
    WebSocketSession session = session(owner);
    websocket.handleTextMessage(session, send(upload(session, "x".repeat(17_000))));
    assertThat(payloads(session))
        .contains("could not read this file within the configured bounds")
        .doesNotContain("chat.completed");
    assertThat(captured().last.get()).isNull();
    assertThat(queries.history(actor, conversation)).isEmpty();
  }

  private DocumentFixtureRuntime fixture() {
    return (DocumentFixtureRuntime) runtime;
  }

  private ChatContextComponentTest.CapturingModel captured() {
    return (ChatContextComponentTest.CapturingModel) model;
  }

  private String upload(WebSocketSession session) throws Exception {
    return upload(session, BODY);
  }

  private String upload(WebSocketSession session, String body) throws Exception {
    websocket.handleTextMessage(
        session,
        message(
            Map.of(
                "type",
                "attachment.put",
                "intent",
                "task-only",
                "name",
                "notes.txt",
                "contentType",
                "text/plain",
                "content",
                Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8)))));
    ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
    verify(session, atLeastOnce()).sendMessage(sent.capture());
    for (TextMessage value : sent.getAllValues()) {
      var event = json.readTree(value.getPayload());
      if (event.path("type").asString().equals("attachment.available"))
        return event.path("attachmentId").asString();
    }
    throw new AssertionError("Upload did not produce an owned attachment");
  }

  private TextMessage send(String id) throws Exception {
    return message(
        Map.of(
            "type",
            "chat.send",
            "conversationId",
            conversation.value(),
            "message",
            QUESTION,
            "attachmentIds",
            List.of(id)));
  }

  private TextMessage message(Map<String, Object> values) throws Exception {
    var all = new HashMap<>(values);
    all.put("protocol", "zalava.ui/v1");
    return new TextMessage(json.writeValueAsString(all));
  }

  private static WebSocketSession session(Account account) {
    var session = mock(WebSocketSession.class);
    when(session.getPrincipal()).thenReturn((Principal) () -> account.loginName());
    when(session.isOpen()).thenReturn(true);
    return session;
  }

  private static String payloads(WebSocketSession session) throws Exception {
    ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
    verify(session, atLeastOnce()).sendMessage(sent.capture());
    return sent.getAllValues().stream().map(TextMessage::getPayload).reduce("", String::concat);
  }
}
