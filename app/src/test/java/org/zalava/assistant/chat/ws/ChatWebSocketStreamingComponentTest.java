package org.zalava.assistant.chat.ws;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountRole;
import reactor.core.publisher.Flux;

/**
 * Full-context component test for the streamed actor chat turn. It drives the real WebSocket
 * handler with the real use cases and a chunked fake {@link ChatModel}, so it verifies the whole
 * progressive pipeline: delta OOB swaps, final bubble with job links, typing-indicator handling,
 * chat-memory persistence across turns, and the model-boundary redaction of streamed deltas.
 */
@SpringBootTest
@Import(ChatWebSocketStreamingComponentTest.ChunkedStreamingChatModelConfiguration.class)
class ChatWebSocketStreamingComponentTest {
  private static final Path WORKSPACE = workspace();
  private static final AtomicInteger LOGINS = new AtomicInteger();
  private static final String[] CHUNKS = {"chunk one ", "chunk two ", "chunk three"};

  @Autowired AccountLifecycle accounts;
  @Autowired ChatWebSocketHandler handler;
  @Autowired MemberChatModel chatModel;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("agent.workspace", () -> WORKSPACE.toUri().toString());
    registry.add("sea.accounts.security-enabled", () -> "true");
    registry.add("agent.onboarding.completed", () -> "true");
    registry.add("spring.ai.model.chat", () -> "unknown");
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
    registry.add("agent.model-boundary.secret-values", () -> "top-secret-value-99");
  }

  @Test
  void aUserMessageStreamsProgressiveDeltasThenTheFinalBubble() throws Exception {
    Account member = member();

    List<String> payloads = turn(member, "stream me a story");

    // Payloads: bootstrap, user echo, then one or more streaming deltas, then the final bubble.
    // Echo of the user message with the typing indicator.
    assertThat(payloads.get(1)).contains("stream me a story").contains("ar-typing");
    // A streaming bubble appears before the final bubble...
    List<String> streamingPayloads =
        payloads.stream().filter(payload -> payload.contains("streaming-bubble")).toList();
    assertThat(streamingPayloads).isNotEmpty();
    // ...and no streamed delta yet carries the completed text.
    assertThat(streamingPayloads.getFirst()).doesNotContain("chunk three");
    // The final payload swaps the streaming bubble for the finished one.
    String last = payloads.getLast();
    assertThat(last)
        .contains("hx-swap-oob=\"delete\"")
        .contains("chunk one chunk two chunk three")
        .contains("typing-indicator")
        .doesNotContain("ar-typing")
        .doesNotContain("read-only view");
  }

  @Test
  void streamedTurnsPersistToChatMemoryForTheNextTurn() throws Exception {
    Account member = member();
    int before = chatModel.prompts.size();
    turn(member, "first question");

    turn(member, "second question");

    assertThat(chatModel.prompts.size()).isGreaterThanOrEqualTo(before + 2);
    Prompt secondTurnPrompt = chatModel.prompts.get(before + 1);
    String assistantText =
        secondTurnPrompt.getInstructions().stream()
            .filter(message -> message.getMessageType() == MessageType.ASSISTANT)
            .map(AssistantMessage.class::cast)
            .map(AssistantMessage::getText)
            .findFirst()
            .orElse("");
    assertThat(assistantText).contains("chunk one chunk two chunk three");
  }

  @Test
  void streamedDeltasNeverExposeAConfiguredSecret() throws Exception {
    Account member = member();

    List<String> payloads = turn(member, "tell me about top-secret-value-99");

    // The user echo repeats the caller's own input by design; every model-output payload must be
    // free of the secret, and the final bubble must carry the redaction.
    List<String> modelOutputs =
        payloads.stream().filter(payload -> !payload.contains("top-secret-value-99")).toList();
    assertThat(modelOutputs).isNotEmpty();
    assertThat(String.join("\n", modelOutputs)).doesNotContain("top-secret-value-99");
    // The model echoed the secret split across chunks; the boundary pulled the emission boundary
    // back to the occurrence start and redacted it as a whole in the completion flush.
    assertThat(payloads.getLast()).contains("the value is [REDACTED] indeed");
  }

  private List<String> turn(Account member, String message) throws Exception {
    WebSocketSession session = authenticatedSession(member);
    try {
      handler.afterConnectionEstablished(session);
      String conversationId = conversationIdFromBootstrap(session);
      handler.handleTextMessage(
          session,
          new TextMessage(
              new tools.jackson.databind.ObjectMapper()
                  .writeValueAsString(
                      java.util.Map.of(
                          "type", "userMessage",
                          "conversationId", conversationId,
                          "message", message))));
      ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
      verify(session, atLeastOnce()).sendMessage(messages.capture());
      return messages.getAllValues().stream().map(TextMessage::getPayload).toList();
    } finally {
      handler.afterConnectionClosed(session, CloseStatus.NORMAL);
    }
  }

  /** The actor bootstrap sends the conversation selector; its selected option holds the id. */
  private static String conversationIdFromBootstrap(WebSocketSession session) throws IOException {
    ArgumentCaptor<TextMessage> messages = ArgumentCaptor.forClass(TextMessage.class);
    verify(session, atLeastOnce()).sendMessage(messages.capture());
    String bootstrap = messages.getAllValues().getFirst().getPayload();
    var matcher = java.util.regex.Pattern.compile("value=\"([0-9a-f-]{36})\"").matcher(bootstrap);
    assertThat(matcher.find()).as("bootstrap contains a selected conversation option").isTrue();
    return matcher.group(1);
  }

  private static WebSocketSession authenticatedSession(Account member) {
    WebSocketSession session = mock(WebSocketSession.class);
    org.mockito.Mockito.when(session.getPrincipal()).thenReturn(() -> member.loginName());
    org.mockito.Mockito.when(session.isOpen()).thenReturn(true);
    return session;
  }

  private Account member() {
    String login = "chat-stream-member-" + LOGINS.incrementAndGet();
    Account account = accounts.create(login, "TemporaryPassword-123", AccountRole.MEMBER);
    accounts.changePassword(account.id(), "TemporaryPassword-123", "PermanentPassword-123");
    return accounts.findByLoginName(login).orElseThrow();
  }

  /**
   * A ChatModel whose stream emits the reply as three separate chunks, mimicking token streaming.
   * Registered as {@code @Primary} so the ChatClient used by the real agent pipeline resolves it
   * instead of the unconfigured default.
   */
  @TestConfiguration(proxyBeanMethods = false)
  static class ChunkedStreamingChatModelConfiguration {

    @Bean
    @Primary
    MemberChatModel memberChatModel() {
      return new MemberChatModel();
    }
  }

  static class MemberChatModel implements ChatModel {

    final List<Prompt> prompts = new ArrayList<>();
    private final ChatOptions options = ToolCallingChatOptions.builder().build();

    @Override
    public ChatResponse call(Prompt prompt) {
      prompts.add(prompt);
      if (prompt.getLastUserOrToolResponseMessage() instanceof ToolResponseMessage) {
        return response("tool result");
      }
      return response(String.join("", CHUNKS));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
      prompts.add(prompt);
      if (prompt.getUserMessage() != null
          && prompt.getUserMessage().getText().contains("[REDACTED]")) {
        // The input boundary already redacted the caller's secret before the model saw it. This
        // branch simulates a model leaking a secret anyway, split across chunks, so the streamed
        // output redaction is exercised against a genuinely straddling occurrence end-to-end.
        return Flux.just("the value is top-", "secret-value-99", " indeed")
            .map(MemberChatModel::response);
      }
      return Flux.fromArray(CHUNKS).map(MemberChatModel::response);
    }

    @Override
    public ChatOptions getOptions() {
      return options;
    }

    private static ChatResponse response(String content) {
      return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }
  }

  private static Path workspace() {
    try {
      Path root = Files.createTempDirectory("chat-ws-streaming-component-");
      Files.writeString(root.resolve("AGENT.md"), "Test agent prompt.");
      Files.writeString(root.resolve("INFO.md"), "Test environment info.");
      return root;
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }
}
