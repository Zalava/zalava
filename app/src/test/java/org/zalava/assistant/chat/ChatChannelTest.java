package org.zalava.assistant.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.zalava.assistant.agent.Agent;
import org.zalava.assistant.channels.ChannelRegistry;
import org.zalava.assistant.channels.approval.ChannelApprovalCommands;
import org.zalava.tasks.capture.TaskCreationContext;
import org.zalava.tasks.domain.TaskReference;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("deprecation")
class ChatChannelTest {

  @Mock Agent agent;
  @Mock ChatMemoryRepository chatMemoryRepository;
  @Mock ChannelApprovalCommands approvalCommands;
  TaskCreationContext taskCreationContext;

  ChatChannel chatChannel;

  @BeforeEach
  void setUp() {
    taskCreationContext = new TaskCreationContext();
    chatChannel =
        new ChatChannel(
            agent,
            new ChannelRegistry(),
            chatMemoryRepository,
            taskCreationContext,
            approvalCommands);
  }

  // -----------------------------------------------------------------------
  // conversationIds
  // -----------------------------------------------------------------------

  @Test
  void conversationIdsAlwaysContainsWebFirst() {
    when(chatMemoryRepository.findConversationIds()).thenReturn(List.of("telegram-42", "web"));

    List<String> ids = chatChannel.conversationIds();

    assertThat(ids).first().isEqualTo("web");
  }

  @Test
  void conversationIdsIncludesWebEvenWhenRepositoryReturnsEmpty() {
    when(chatMemoryRepository.findConversationIds()).thenReturn(List.of());

    List<String> ids = chatChannel.conversationIds();

    assertThat(ids).containsExactly("web");
  }

  @Test
  void conversationIdsIncludesOtherChannelsAfterWeb() {
    when(chatMemoryRepository.findConversationIds())
        .thenReturn(List.of("telegram-42", "telegram-99"));

    List<String> ids = chatChannel.conversationIds();

    assertThat(ids).containsExactly("web", "telegram-42", "telegram-99");
  }

  @Test
  void conversationIdsDeduplicatesWeb() {
    when(chatMemoryRepository.findConversationIds()).thenReturn(List.of("web", "telegram-42"));

    List<String> ids = chatChannel.conversationIds();

    assertThat(ids.stream().filter("web"::equals)).hasSize(1);
  }

  @Test
  void createWebConversationPersistsEmptyWebConversation() {
    String conversationId = chatChannel.createWebConversation();

    assertThat(conversationId).startsWith("web-");
    verify(chatMemoryRepository).saveAll(conversationId, List.of());
  }

  // -----------------------------------------------------------------------
  // loadHistoryAsHtml
  // -----------------------------------------------------------------------

  @Test
  void loadHistoryReturnsWelcomeBubbleWhenNoHistory() {
    when(chatMemoryRepository.findByConversationId("web")).thenReturn(List.of());

    List<String> bubbles = chatChannel.loadHistoryAsHtml("web");

    assertThat(bubbles).hasSize(1);
    assertThat(bubbles.get(0)).contains("ar-msg--agent").contains("Zalava assistant");
  }

  @Test
  void loadHistoryRendersUserAndAgentBubbles() {
    when(chatMemoryRepository.findByConversationId("web"))
        .thenReturn(List.of(new UserMessage("Hello"), new AssistantMessage("Hi there")));

    List<String> bubbles = chatChannel.loadHistoryAsHtml("web");

    assertThat(bubbles).hasSize(2);
    assertThat(bubbles.get(0)).contains("ar-msg--user").contains("Hello");
    assertThat(bubbles.get(1)).contains("ar-msg--agent").contains("Hi there");
  }

  @Test
  void loadHistoryEscapesHtmlInMessages() {
    when(chatMemoryRepository.findByConversationId("web"))
        .thenReturn(List.of(new UserMessage("<script>alert('xss')</script>")));

    List<String> bubbles = chatChannel.loadHistoryAsHtml("web");

    assertThat(bubbles.get(0)).doesNotContain("<script>").contains("&lt;script&gt;");
  }

  @Test
  void loadHistoryUsesSuppliedConversationId() {
    when(chatMemoryRepository.findByConversationId("telegram-42")).thenReturn(List.of());

    chatChannel.loadHistoryAsHtml("telegram-42");

    verify(chatMemoryRepository).findByConversationId("telegram-42");
  }

  // -----------------------------------------------------------------------
  // chat
  // -----------------------------------------------------------------------

  @Test
  void chatDelegatesToAgentWithConversationId() {
    when(approvalCommands.handle("hello")).thenReturn(Optional.empty());
    when(agent.respondTo("web", "hello")).thenReturn("hi");

    ChatTurnResult response = chatChannel.chat("web", "hello");

    assertThat(response.text()).isEqualTo("hi");
    assertThat(response.jobReferences()).isEmpty();
    verify(agent).respondTo(eq("web"), eq("hello"));
  }

  @Test
  void chatUsesSuppliedConversationId() {
    when(approvalCommands.handle("hello")).thenReturn(Optional.empty());
    when(agent.respondTo(eq("telegram-42"), any())).thenReturn("reply");

    chatChannel.chat("telegram-42", "hello");

    verify(agent).respondTo(eq("telegram-42"), eq("hello"));
  }

  @Test
  void chatReturnsTasksCreatedDuringTheTurn() {
    TaskReference reference = TaskReference.parse("2026-06-08", "120000-write-summary.md");
    when(approvalCommands.handle("create a task")).thenReturn(Optional.empty());
    when(agent.respondTo("web", "create a task"))
        .thenAnswer(
            invocation -> {
              taskCreationContext.taskCreated(reference, "write-summary", "Write the summary");
              return "I created a task.";
            });

    ChatTurnResult response = chatChannel.chat("web", "create a task");

    assertThat(response.text()).isEqualTo("I created a task.");
    assertThat(response.jobReferences()).containsExactly(reference);
  }

  @Test
  void chatHandlesApprovalCommandWithoutCallingAgent() {
    when(approvalCommands.handle("/zalava approve approval-123"))
        .thenReturn(Optional.of("Approval granted once."));

    ChatTurnResult response = chatChannel.chat("web", "/zalava approve approval-123");

    assertThat(response.text()).isEqualTo("Approval granted once.");
    assertThat(response.jobReferences()).isEmpty();
    verify(agent, never()).respondTo(any(), any());
  }

  // -----------------------------------------------------------------------
  // sendMessage / WebSocket session
  // -----------------------------------------------------------------------

  @Test
  void sendHtmlDoesNothingWhenNoSessionSet() throws IOException {
    // should not throw
    chatChannel.sendHtml("<div>test</div>");
  }

  @Test
  void sendHtmlWritesToActiveSession() throws IOException {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.isOpen()).thenReturn(true);
    chatChannel.setWsSession(session);

    chatChannel.sendHtml("<div>test</div>");

    verify(session).sendMessage(new TextMessage("<div>test</div>"));
  }

  @Test
  void sendHtmlDoesNothingWhenSessionIsClosed() throws IOException {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.isOpen()).thenReturn(false);
    chatChannel.setWsSession(session);

    chatChannel.sendHtml("<div>test</div>");

    verify(session, never()).sendMessage(any());
  }

  @Test
  void clearWsSessionRemovesSession() throws IOException {
    WebSocketSession session = mock(WebSocketSession.class);
    chatChannel.setWsSession(session);
    chatChannel.clearWsSession(session);

    chatChannel.sendHtml("<div>test</div>");

    verify(session, never()).sendMessage(any());
  }

  @Test
  void clearWsSessionIgnoresDifferentSession() throws IOException {
    WebSocketSession session = mock(WebSocketSession.class);
    WebSocketSession otherSession = mock(WebSocketSession.class);
    when(session.isOpen()).thenReturn(true);
    chatChannel.setWsSession(session);
    chatChannel.clearWsSession(otherSession);

    chatChannel.sendHtml("<div>test</div>");

    verify(session).sendMessage(any());
  }

  @Test
  void sendMessagePushesOobHtmlToActiveSession() throws IOException {
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.isOpen()).thenReturn(true);
    chatChannel.setWsSession(session);

    chatChannel.sendMessage("Background result");

    verify(session).sendMessage(any(TextMessage.class));
  }
}
