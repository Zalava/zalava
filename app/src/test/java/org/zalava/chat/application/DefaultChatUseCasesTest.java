package org.zalava.chat.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.zalava.chat.application.port.out.ChatAgent;
import org.zalava.chat.application.port.out.ChatApprovalCommands;
import org.zalava.chat.application.port.out.ChatConversationIdGenerator;
import org.zalava.chat.application.port.out.ChatConversationStore;
import org.zalava.chat.application.port.out.ChatMessageEvents;
import org.zalava.chat.domain.ChatMessage;
import org.zalava.chat.domain.ChatTurn;
import org.junit.jupiter.api.Test;

class DefaultChatUseCasesTest {
  private final ChatAgent agent = mock(ChatAgent.class);
  private final ChatApprovalCommands approvals = mock(ChatApprovalCommands.class);
  private final ChatConversationStore conversations = mock(ChatConversationStore.class);
  private final ChatMessageEvents events = mock(ChatMessageEvents.class);
  private final ChatConversationIdGenerator ids = mock(ChatConversationIdGenerator.class);
  private final DefaultChatUseCases useCases =
      new DefaultChatUseCases(agent, approvals, conversations, events, ids);

  @Test
  void publishesThenDelegatesChatTurnToAgent() {
    ChatTurn expected = new ChatTurn("hello", List.of());
    when(approvals.handle("hi")).thenReturn(Optional.empty());
    when(agent.respondTo("web", "hi")).thenReturn(expected);

    assertThat(useCases.chat("web", "hi")).isEqualTo(expected);

    verify(events).publishReceived(DefaultChatUseCases.WEB_CHANNEL_NAME, "hi");
    verify(agent).respondTo("web", "hi");
  }

  @Test
  void handlesApprovalWithoutCallingAgent() {
    when(approvals.handle("/sea approve a")).thenReturn(Optional.of("Approved"));

    ChatTurn result = useCases.chat("web", "/sea approve a");

    assertThat(result.text()).isEqualTo("Approved");
    assertThat(result.taskReferences()).isEmpty();
    verify(agent, never()).respondTo("web", "/sea approve a");
  }

  @Test
  void keepsWebFirstAndPersistsGeneratedConversation() {
    when(conversations.findConversationIds()).thenReturn(List.of("telegram-1", "web"));
    when(ids.nextWebConversationId()).thenReturn("web-new");

    assertThat(useCases.conversationIds()).containsExactly("web", "telegram-1");
    assertThat(useCases.createWebConversation()).isEqualTo("web-new");
    verify(conversations).saveEmptyConversation("web-new");
  }

  @Test
  void returnsStoredHistoryWithoutFrameworkMapping() {
    List<ChatMessage> history = List.of(new ChatMessage(ChatMessage.Role.USER, "Hello"));
    when(conversations.findByConversationId("web")).thenReturn(history);

    assertThat(useCases.history("web")).isEqualTo(history);
  }
}
