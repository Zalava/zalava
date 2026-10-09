package org.zalava.assistant.chat.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.zalava.assistant.chat.api.ChatProviderReadiness;
import org.zalava.assistant.chat.application.UiExecutionStateQueries;
import org.zalava.assistant.chat.application.port.in.ActorChatCommands;
import org.zalava.assistant.chat.application.port.in.ActorChatQueries;
import org.zalava.assistant.chat.application.port.in.ActorChatStreamListener;
import org.zalava.assistant.chat.domain.ActorChatTurn;
import org.zalava.assistant.chat.domain.ChatMessage;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.identity.accounts.security.AuthenticatedActorResolver;
import tools.jackson.databind.ObjectMapper;

class UiChatWebSocketHandlerTest {
  private final ObjectMapper json = new ObjectMapper();
  private final AuthenticatedActorResolver actors = mock(AuthenticatedActorResolver.class);
  private final ActorChatCommands commands = mock(ActorChatCommands.class);
  private final ActorChatQueries queries = mock(ActorChatQueries.class);
  private final WebSocketSession session = mock(WebSocketSession.class);
  private final Actor actor = new Actor(AccountId.newId());
  private final ConversationReference conversation = ConversationReference.newReference();

  @Test
  void unconfiguredModelRejectsCraftedSendBeforeExecutingOrPersistingChat() throws Exception {
    when(session.getPrincipal()).thenReturn((Principal) () -> "member");
    when(session.isOpen()).thenReturn(true);
    when(actors.actorForLogin("member")).thenReturn(actor);
    var readiness =
        new ChatProviderReadiness(
            new MockEnvironment().withProperty("spring.ai.model.chat", "unknown"));
    var handler =
        new UiChatWebSocketHandler(
            json, actors, commands, queries, null, null, null, null, readiness);
    handler.handleTextMessage(
        session,
        new TextMessage(
            json.writeValueAsString(
                Map.of(
                    "protocol",
                    "zalava.ui/v1",
                    "type",
                    "chat.send",
                    "conversationId",
                    conversation.value(),
                    "message",
                    "Hello"))));
    var sent = ArgumentCaptor.forClass(TextMessage.class);
    verify(session).sendMessage(sent.capture());
    assertThat(sent.getValue().getPayload()).contains("Model not configured");
    verifyNoInteractions(commands, queries);
  }

  @Test
  void bootstrapsHistoryAndStreamsTypedEventsForTheAuthenticatedActor() throws Exception {
    when(session.getPrincipal()).thenReturn((Principal) () -> "member");
    when(session.isOpen()).thenReturn(true);
    when(actors.actorForLogin("member")).thenReturn(actor);
    when(queries.conversations(actor)).thenReturn(List.of(conversation));
    when(queries.history(actor, conversation))
        .thenReturn(List.of(new ChatMessage(ChatMessage.Role.USER, "Earlier")));
    doAnswer(
            invocation -> {
              var listener = invocation.getArgument(3, ActorChatStreamListener.class);
              listener.onDelta("Hello ");
              listener.onComplete("Hello Zalava", List.of());
              return new ActorChatTurn("Hello Zalava", List.of());
            })
        .when(commands)
        .streamChat(any(), any(), any(), any());
    var handler = new UiChatWebSocketHandler(json, actors, commands, queries);

    handler.afterConnectionEstablished(session);
    handler.handleTextMessage(
        session,
        new TextMessage(
            json.writeValueAsString(
                java.util.Map.of(
                    "protocol", "zalava.ui/v1",
                    "type", "chat.send",
                    "conversationId", conversation.value(),
                    "message", "Hello"))));

    var sent = ArgumentCaptor.forClass(TextMessage.class);
    verify(session, org.mockito.Mockito.atLeast(3)).sendMessage(sent.capture());
    String payloads =
        sent.getAllValues().stream().map(TextMessage::getPayload).reduce("", String::concat);
    assertThat(payloads)
        .contains(
            "conversation.list",
            conversation.value(),
            "conversation.snapshot",
            "Earlier",
            "chat.delta",
            "chat.completed");
    verify(commands).streamChat(eq(actor), eq(conversation), eq("Hello"), any());
  }

  @Test
  void publishesTheUpdatedConversationListAfterCreatingAConversation() throws Exception {
    when(session.getPrincipal()).thenReturn((Principal) () -> "member");
    when(session.isOpen()).thenReturn(true);
    when(actors.actorForLogin("member")).thenReturn(actor);
    when(queries.conversations(actor)).thenReturn(List.of(conversation));
    when(queries.history(actor, conversation)).thenReturn(List.of());
    when(commands.createWebConversation(actor)).thenReturn(conversation);

    var handler = new UiChatWebSocketHandler(json, actors, commands, queries);
    handler.handleTextMessage(
        session, new TextMessage("{\"protocol\":\"zalava.ui/v1\",\"type\":\"chat.create\"}"));

    var sent = ArgumentCaptor.forClass(TextMessage.class);
    verify(session, org.mockito.Mockito.atLeast(2)).sendMessage(sent.capture());
    String payloads =
        sent.getAllValues().stream().map(TextMessage::getPayload).reduce("", String::concat);
    assertThat(payloads)
        .contains("conversation.snapshot", "conversation.list", conversation.value());
    verify(commands).createWebConversation(actor);
  }

  @Test
  void rejectsSelectingAnotherActorsConversationWithoutSendingItsHistory() throws Exception {
    var otherActorsConversation = ConversationReference.newReference();
    when(session.getPrincipal()).thenReturn((Principal) () -> "member");
    when(session.isOpen()).thenReturn(true);
    when(actors.actorForLogin("member")).thenReturn(actor);
    when(queries.history(actor, otherActorsConversation))
        .thenThrow(new IllegalArgumentException("Conversation not found"));

    var handler = new UiChatWebSocketHandler(json, actors, commands, queries);
    handler.handleTextMessage(
        session,
        new TextMessage(
            json.writeValueAsString(
                java.util.Map.of(
                    "protocol", "zalava.ui/v1",
                    "type", "chat.select",
                    "conversationId", otherActorsConversation.value()))));

    var sent = ArgumentCaptor.forClass(TextMessage.class);
    verify(session).sendMessage(sent.capture());
    assertThat(sent.getValue().getPayload())
        .contains("failure", "Zalava could not complete that request")
        .doesNotContain(otherActorsConversation.value(), "conversation.snapshot");
    verify(queries).history(actor, otherActorsConversation);
  }

  @Test
  void rejectsMalformedCommandsWithoutCallingAUseCase() throws Exception {
    when(session.isOpen()).thenReturn(true);
    var handler = new UiChatWebSocketHandler(json, actors, commands, queries);

    handler.handleTextMessage(session, new TextMessage("{\"type\":\"permission.grant\"}"));

    var sent = ArgumentCaptor.forClass(TextMessage.class);
    verify(session).sendMessage(sent.capture());
    assertThat(sent.getValue().getPayload())
        .contains("failure", "Unsupported or invalid UI command");
  }

  @Test
  void replaysBoundedExecutionAndPendingApprovalStateForTheAuthenticatedActor() throws Exception {
    var executionStates = mock(UiExecutionStateQueries.class);
    var approvalDecisions = mock(UiApprovalDecisions.class);
    var state =
        new UiExecutionStateQueries.ExecutionState(
            "job-1",
            "awaiting_human_input",
            "Write report",
            List.of(new UiExecutionStateQueries.PendingApproval("request-1", "files/write")));
    when(session.getPrincipal()).thenReturn((Principal) () -> "member");
    when(session.isOpen()).thenReturn(true);
    when(actors.actorForLogin("member")).thenReturn(actor);
    when(queries.conversations(actor)).thenReturn(List.of(conversation));
    when(queries.history(actor, conversation)).thenReturn(List.of());
    when(executionStates.states(actor)).thenReturn(List.of(state));

    var handler =
        new UiChatWebSocketHandler(
            json, actors, commands, queries, executionStates, approvalDecisions);
    handler.afterConnectionEstablished(session);

    var sent = ArgumentCaptor.forClass(TextMessage.class);
    verify(session, org.mockito.Mockito.atLeast(4)).sendMessage(sent.capture());
    assertThat(sent.getAllValues().stream().map(TextMessage::getPayload).reduce("", String::concat))
        .contains("job.updated", "job-1", "permission.requested", "request-1", "files/write");
  }
}
