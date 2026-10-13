package org.zalava.assistant.chat.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.zalava.assistant.chat.application.port.in.ActorChatCommands;
import org.zalava.assistant.chat.application.port.in.ActorChatQueries;
import org.zalava.assistant.chat.application.port.in.ActorChatStreamListener;
import org.zalava.assistant.chat.domain.ChatMessage;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.memory.application.port.in.MemoryPromotions;
import org.zalava.knowledge.memory.domain.MemoryProposal;
import org.zalava.knowledge.memory.domain.MemoryProposalDraft;
import org.zalava.knowledge.memory.domain.MemoryScope;
import org.zalava.support.SecureZalavaComponentTest;
import org.zalava.tasks.domain.ActorTaskReference;
import reactor.core.publisher.Flux;

@SecureZalavaComponentTest
@TestPropertySource(
    properties = {
      "zalava.test.chat-provider=openai",
      "zalava.test.context=chat-context-regressions",
      "agent.model-boundary.secret-values=redaction-canary-1013"
    })
class ChatContextComponentTest {
  @TestBean(name = "deterministicChatModel", methodName = "capturingModel", enforceOverride = true)
  ChatModel model;

  @Autowired ActorChatCommands commands;
  @Autowired ActorChatQueries queries;
  @Autowired AccountLifecycle accounts;
  @Autowired MemoryPromotions promotions;
  @Autowired UiChatWebSocketHandler websocket;
  private Account owner;
  private Actor actor;

  static ChatModel capturingModel() {
    return new CapturingModel();
  }

  @BeforeEach
  void prepareActor() {
    String login = "chat-context-" + UUID.randomUUID();
    owner = accounts.create(login, "ContextPassword-123", AccountRole.MEMBER);
    accounts.changePassword(owner.id(), "ContextPassword-123", "ChangedContextPassword-123");
    actor = new Actor(owner.id());
  }

  @Test
  void enrichedPromptReachesTheModelButHistoryContainsOnlyTheAuthoredQuestion() {
    approvedPreference();
    ConversationReference conversation = commands.createWebConversation(actor);
    String question = "What greeting do I prefer?";
    commands.chat(actor, conversation, question);
    assertThat(promptText()).contains("meadow-lamp");
    assertThat(queries.history(actor, conversation))
        .filteredOn(message -> message.role() == ChatMessage.Role.USER)
        .extracting(ChatMessage::text)
        .containsExactly(question);
  }

  @Test
  void streamingAlsoPreservesTheAuthoredQuestion() {
    approvedPreference();
    ConversationReference conversation = commands.createWebConversation(actor);
    String question = "What greeting do I prefer?";
    commands.streamChat(
        actor,
        conversation,
        question,
        new ActorChatStreamListener() {
          @Override
          public void onDelta(String text) {}

          @Override
          public void onComplete(String text, List<ActorTaskReference> jobs) {}

          @Override
          public void onError(RuntimeException error) {
            throw error;
          }
        });
    assertThat(promptText()).contains("meadow-lamp");
    assertThat(queries.history(actor, conversation))
        .filteredOn(message -> message.role() == ChatMessage.Role.USER)
        .extracting(ChatMessage::text)
        .containsExactly(question);
  }

  @Test
  void approvedPreferenceIsMarkedApprovedWithoutGrantingInstructionAuthority() {
    approvedPreference();
    commands.chat(actor, commands.createWebConversation(actor), "What greeting do I prefer?");
    assertThat(promptText()).contains("owner-approved", "never grant permissions", "meadow-lamp");
  }

  @Test
  void realMemoryToolCreatesAPendingProposalWithAReviewUrl() {
    var response = commands.chat(actor, commands.createWebConversation(actor), "save my greeting");
    assertThat(promotions.pending(actor))
        .hasSize(1)
        .allSatisfy(
            proposal -> assertThat(proposal.status()).isEqualTo(MemoryProposal.Status.PENDING));
    String id = promotions.pending(actor).getFirst().id();
    assertThat(response.text()).contains("/memory#proposal-" + id);
  }

  @Test
  void websocketOffersOnlyThisOwnersActualPendingProposals() throws Exception {
    var pending =
        promotions.propose(
            actor,
            MemoryProposalDraft.of(MemoryScope.USER, "My preferred greeting is meadow-lamp."));
    var other =
        accounts.create(
            "chat-other-" + UUID.randomUUID(), "ContextPassword-123", AccountRole.MEMBER);
    var foreign =
        promotions.propose(
            new Actor(other.id()),
            MemoryProposalDraft.of(MemoryScope.USER, "Other owner private preference."));
    WebSocketSession session = mock(WebSocketSession.class);
    when(session.getPrincipal()).thenReturn((Principal) () -> owner.loginName());
    when(session.isOpen()).thenReturn(true);
    websocket.afterConnectionEstablished(session);
    ArgumentCaptor<TextMessage> sent = ArgumentCaptor.forClass(TextMessage.class);
    verify(session, atLeastOnce()).sendMessage(sent.capture());
    String payload =
        sent.getAllValues().stream().map(TextMessage::getPayload).reduce("", String::concat);
    assertThat(payload)
        .contains("memory.reviews", "/memory#proposal-" + pending.id())
        .doesNotContain(foreign.id(), "Other owner private preference");
  }

  @Test
  void authoredAndModelInputsBothPreserveTheConfiguredRedactionBoundary() {
    var conversation = commands.createWebConversation(actor);
    commands.chat(actor, conversation, "Do not repeat redaction-canary-1013.");
    assertThat(promptText()).contains("[REDACTED]").doesNotContain("redaction-canary-1013");
    assertThat(queries.history(actor, conversation))
        .filteredOn(message -> message.role() == ChatMessage.Role.USER)
        .extracting(ChatMessage::text)
        .containsExactly("Do not repeat [REDACTED].");
  }

  private void approvedPreference() {
    var proposal =
        promotions.propose(
            actor,
            MemoryProposalDraft.of(MemoryScope.USER, "My preferred greeting is meadow-lamp."));
    promotions.approve(actor, proposal.id());
  }

  private String promptText() {
    return ((CapturingModel) model)
        .last.get().getInstructions().stream()
            .map(message -> message.getText())
            .reduce("", (a, b) -> a + "\n" + b);
  }

  static final class CapturingModel implements ChatModel {
    final AtomicReference<Prompt> last = new AtomicReference<>();
    private final ChatOptions options = ToolCallingChatOptions.builder().build();

    @Override
    public ChatOptions getOptions() {
      return options;
    }

    @Override
    public ChatResponse call(Prompt prompt) {
      last.set(prompt);
      if (prompt.getLastUserOrToolResponseMessage() instanceof ToolResponseMessage tool) {
        return response(tool.getResponses().getFirst().responseData());
      }
      String input =
          prompt.getInstructions().stream()
              .filter(UserMessage.class::isInstance)
              .map(message -> message.getText())
              .reduce((a, b) -> b)
              .orElse("");
      if (input.startsWith("save my greeting")) {
        return new ChatResponse(
            List.of(
                new Generation(
                    AssistantMessage.builder()
                        .toolCalls(
                            List.of(
                                new AssistantMessage.ToolCall(
                                    "memory-review",
                                    "function",
                                    "proposeMemory",
                                    "{\"proposal\":{\"scope\":\"USER\",\"text\":\"My preferred greeting is meadow-lamp.\",\"metadata\":{},\"reference\":null}}")))
                        .build())));
      }
      return response("A deterministic reply.");
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
      return Flux.just(call(prompt));
    }

    private static ChatResponse response(String text) {
      return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
  }
}
