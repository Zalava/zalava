package org.zalava.assistant.conversation.adapter.out.springai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

class MessageChatMemoryAdvisorTest {

  private static final String CONVERSATION_ID = "conv";

  @Test
  void builderDefaultsMatchTheFrameworkMemoryPrecedence() {
    MessageChatMemoryAdvisor advisor =
        MessageChatMemoryAdvisor.builder(new RecordingChatMemory()).build();

    assertThat(advisor.getOrder()).isEqualTo(Advisor.DEFAULT_CHAT_MEMORY_PRECEDENCE_ORDER);
  }

  @Test
  void beforeRemovesInstructionMessagesAlreadyPresentInMemory() {
    RecordingChatMemory memory = new RecordingChatMemory();
    memory.add(CONVERSATION_ID, List.of(new UserMessage("hello")));
    MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(memory).build();

    ChatClientRequest processed =
        advisor.before(
            request(new SystemMessage("rules"), new UserMessage("hello"), new UserMessage("world")),
            null);

    assertThat(processed.prompt().getInstructions())
        .extracting(Message::getText)
        .containsExactly("rules", "hello", "world");
    assertThat(memory.storedTexts()).containsExactly("hello", "world");
  }

  @Test
  void beforeMovesTheSystemMessageToTheFrontAndAppendsTheNewUserMessage() {
    RecordingChatMemory memory = new RecordingChatMemory();
    MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(memory).build();

    ChatClientRequest processed =
        advisor.before(request(new UserMessage("question"), new SystemMessage("rules")), null);

    assertThat(processed.prompt().getInstructions())
        .extracting(Message::getText)
        .containsExactly("rules", "question");
    assertThat(memory.storedTexts()).containsExactly("question");
  }

  @Test
  void beforeAppendsTheLastToolResponseWhenTheTurnEndsWithAToolResult() {
    RecordingChatMemory memory = new RecordingChatMemory();
    MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(memory).build();
    ToolResponseMessage toolResponse =
        ToolResponseMessage.builder()
            .responses(
                List.of(new ToolResponseMessage.ToolResponse("call-1", "lookup", "tool-result")))
            .build();

    advisor.before(
        request(new SystemMessage("rules"), new UserMessage("question"), toolResponse), null);

    assertThat(memory.storedMessages()).singleElement().isSameAs(toolResponse);
  }

  @Test
  void afterAppendsAssistantOutputToMemory() {
    RecordingChatMemory memory = new RecordingChatMemory();
    MessageChatMemoryAdvisor advisor = MessageChatMemoryAdvisor.builder(memory).build();

    advisor.after(response(new AssistantMessage("answer")), null);

    assertThat(memory.storedTexts()).containsExactly("answer");
  }

  @Test
  void adviseStreamRunsBeforeAndAggregatesAssistantOutput() {
    RecordingChatMemory memory = new RecordingChatMemory();
    MessageChatMemoryAdvisor advisor =
        MessageChatMemoryAdvisor.builder(memory).scheduler(Schedulers.immediate()).build();
    ChatClientResponse streamed = response(new AssistantMessage("streamed-answer"));
    StreamAdvisorChain chain =
        new StreamAdvisorChain() {
          @Override
          public Flux<ChatClientResponse> nextStream(ChatClientRequest request) {
            return Flux.just(streamed);
          }

          @Override
          public List<StreamAdvisor> getStreamAdvisors() {
            return List.of();
          }

          @Override
          public StreamAdvisorChain copy(StreamAdvisor advisor) {
            return this;
          }
        };

    List<ChatClientResponse> emitted =
        advisor
            .adviseStream(request(new SystemMessage("rules"), new UserMessage("question")), chain)
            .collectList()
            .block();

    assertThat(emitted).containsExactly(streamed);
    assertThat(memory.storedTexts()).containsExactly("question", "streamed-answer");
  }

  private static ChatClientRequest request(Message... messages) {
    return ChatClientRequest.builder()
        .prompt(new Prompt(List.of(messages)))
        .context(ChatMemory.CONVERSATION_ID, CONVERSATION_ID)
        .build();
  }

  private static ChatClientResponse response(AssistantMessage assistantMessage) {
    return ChatClientResponse.builder()
        .chatResponse(new ChatResponse(List.of(new Generation(assistantMessage))))
        .context(ChatMemory.CONVERSATION_ID, CONVERSATION_ID)
        .build();
  }

  private static final class RecordingChatMemory implements ChatMemory {

    private final List<Message> messages = new ArrayList<>();

    @Override
    public void add(String conversationId, List<Message> messages) {
      this.messages.addAll(messages);
    }

    @Override
    public List<Message> get(String conversationId) {
      return List.copyOf(messages);
    }

    @Override
    public void clear(String conversationId) {
      messages.clear();
    }

    List<Message> storedMessages() {
      return List.copyOf(messages);
    }

    List<String> storedTexts() {
      return messages.stream().map(Message::getText).toList();
    }
  }
}
