package org.zalava.assistant.conversation.adapter.out.springai;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

class MessageWindowChatMemoryTest {

  static final String CONVERSATION_ID = "test-conv";
  static final int MAX_MESSAGES = 5;

  InMemoryChatMemoryRepository repository;
  MessageWindowChatMemory memory;

  @BeforeEach
  void setUp() {
    repository = new InMemoryChatMemoryRepository();
    memory =
        MessageWindowChatMemory.builder()
            .chatMemoryRepository(repository)
            .maxMessages(MAX_MESSAGES)
            .build();
  }

  // -----------------------------------------------------------------------
  // add
  // -----------------------------------------------------------------------

  @Test
  void addStoresAllMessagesInRepository() {
    memory.add(CONVERSATION_ID, List.of(new UserMessage("msg1"), new AssistantMessage("msg2")));
    memory.add(CONVERSATION_ID, List.of(new UserMessage("msg3")));

    List<Message> stored = repository.findByConversationId(CONVERSATION_ID);
    assertThat(stored).hasSize(3);
    assertThat(stored.get(0).getText()).isEqualTo("msg1");
    assertThat(stored.get(1).getText()).isEqualTo("msg2");
    assertThat(stored.get(2).getText()).isEqualTo("msg3");
  }

  @Test
  void addDoesNotTrimRepositoryWhenExceedingMaxMessages() {
    List<Message> messages =
        IntStream.rangeClosed(1, MAX_MESSAGES + 3)
            .mapToObj(i -> (Message) new UserMessage("msg" + i))
            .toList();

    memory.add(CONVERSATION_ID, messages);

    // repository contains ALL messages, not just last maxMessages
    List<Message> stored = repository.findByConversationId(CONVERSATION_ID);
    assertThat(stored).hasSize(MAX_MESSAGES + 3);
  }

  // -----------------------------------------------------------------------
  // get – windowed view
  // -----------------------------------------------------------------------

  @Test
  void getReturnsAllMessagesWhenBelowMaxMessages() {
    memory.add(CONVERSATION_ID, List.of(new UserMessage("hello"), new AssistantMessage("hi")));

    List<Message> result = memory.get(CONVERSATION_ID);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getText()).isEqualTo("hello");
    assertThat(result.get(1).getText()).isEqualTo("hi");
  }

  @Test
  void getReturnsAllMessagesWhenExactlyMaxMessages() {
    List<Message> messages =
        IntStream.rangeClosed(1, MAX_MESSAGES)
            .mapToObj(i -> (Message) new UserMessage("msg" + i))
            .toList();
    memory.add(CONVERSATION_ID, messages);

    List<Message> result = memory.get(CONVERSATION_ID);

    assertThat(result).hasSize(MAX_MESSAGES);
  }

  @Test
  void getReturnsWindowedViewWhenExceedingMaxMessages() {
    List<Message> messages =
        IntStream.rangeClosed(1, MAX_MESSAGES + 3)
            .mapToObj(i -> (Message) new UserMessage("msg" + i))
            .toList();
    memory.add(CONVERSATION_ID, messages);

    List<Message> result = memory.get(CONVERSATION_ID);

    assertThat(result).hasSize(MAX_MESSAGES);
    // should contain the LAST maxMessages messages
    assertThat(result.get(0).getText()).isEqualTo("msg4");
    assertThat(result.get(result.size() - 1).getText()).isEqualTo("msg" + (MAX_MESSAGES + 3));
  }

  @Test
  void getPreservesSystemMessageWhenTrimming() {
    SystemMessage system = new SystemMessage("You are a helpful assistant.");
    memory.add(CONVERSATION_ID, List.of(system));
    // add enough messages to exceed the window
    List<Message> extras =
        IntStream.rangeClosed(1, MAX_MESSAGES + 2)
            .mapToObj(i -> (Message) new UserMessage("msg" + i))
            .toList();
    memory.add(CONVERSATION_ID, extras);

    List<Message> result = memory.get(CONVERSATION_ID);

    // system message is always preserved
    assertThat(result).hasSize(MAX_MESSAGES);
    assertThat(result.get(0)).isInstanceOf(SystemMessage.class);
    assertThat(result.get(0).getText()).isEqualTo("You are a helpful assistant.");
  }

  @Test
  void getWindowDoesNotMutateRepository() {
    List<Message> messages =
        IntStream.rangeClosed(1, MAX_MESSAGES + 5)
            .mapToObj(i -> (Message) new UserMessage("msg" + i))
            .toList();
    memory.add(CONVERSATION_ID, messages);

    // call get multiple times
    memory.get(CONVERSATION_ID);
    memory.get(CONVERSATION_ID);

    // repository still has all original messages
    assertThat(repository.findByConversationId(CONVERSATION_ID)).hasSize(MAX_MESSAGES + 5);
  }

  @Test
  void getReturnsEmptyListForUnknownConversation() {
    assertThat(memory.get("unknown")).isEmpty();
  }

  // -----------------------------------------------------------------------
  // clear
  // -----------------------------------------------------------------------

  @Test
  void clearRemovesAllMessagesFromRepository() {
    memory.add(CONVERSATION_ID, List.of(new UserMessage("hello"), new AssistantMessage("hi")));

    memory.clear(CONVERSATION_ID);

    assertThat(memory.get(CONVERSATION_ID)).isEmpty();
    assertThat(repository.findByConversationId(CONVERSATION_ID)).isEmpty();
  }

  // -----------------------------------------------------------------------
  // default maxMessages
  // -----------------------------------------------------------------------

  @Test
  void defaultMaxMessagesIs20() {
    InMemoryChatMemoryRepository defaultRepo = new InMemoryChatMemoryRepository();
    MessageWindowChatMemory memoryWithDefaultMax =
        MessageWindowChatMemory.builder().chatMemoryRepository(defaultRepo).build();

    List<Message> messages =
        IntStream.rangeClosed(1, 25).mapToObj(i -> (Message) new UserMessage("msg" + i)).toList();
    memoryWithDefaultMax.add(CONVERSATION_ID, messages);

    List<Message> result = memoryWithDefaultMax.get(CONVERSATION_ID);
    assertThat(result).hasSize(20);
    assertThat(result.get(0).getText()).isEqualTo("msg6");
    assertThat(result.get(19).getText()).isEqualTo("msg25");

    assertThat(defaultRepo.findByConversationId(CONVERSATION_ID)).hasSize(25);
  }

  // -----------------------------------------------------------------------
  // system-message window boundaries
  // -----------------------------------------------------------------------

  @Test
  void getWindowPreservesEverySystemMessageInInsertionOrder() {
    memory.add(CONVERSATION_ID, List.of(new SystemMessage("rules-1")));
    memory.add(
        CONVERSATION_ID,
        IntStream.rangeClosed(1, MAX_MESSAGES + 2)
            .mapToObj(i -> (Message) new UserMessage("msg" + i))
            .toList());
    memory.add(CONVERSATION_ID, List.of(new SystemMessage("rules-2")));

    List<Message> result = memory.get(CONVERSATION_ID);

    assertThat(result).hasSize(MAX_MESSAGES);
    assertThat(result.get(0)).isInstanceOf(SystemMessage.class);
    assertThat(result.get(1)).isInstanceOf(SystemMessage.class);
    assertThat(result.get(0).getText()).isEqualTo("rules-1");
    assertThat(result.get(1).getText()).isEqualTo("rules-2");
    assertThat(result.subList(2, result.size()))
        .allMatch(message -> message instanceof UserMessage);
  }

  @Test
  void getWindowKeepsEverySystemMessageEvenWhenTheyExceedTheMaximum() {
    memory.add(
        CONVERSATION_ID,
        IntStream.rangeClosed(1, MAX_MESSAGES + 1)
            .mapToObj(i -> (Message) new SystemMessage("rules" + i))
            .toList());
    memory.add(CONVERSATION_ID, List.of(new UserMessage("hello")));

    List<Message> result = memory.get(CONVERSATION_ID);

    assertThat(result).allMatch(message -> message instanceof SystemMessage);
    assertThat(result).hasSize(MAX_MESSAGES + 1);
    assertThat(result).extracting(Message::getText).doesNotContain("hello");
  }

  // -----------------------------------------------------------------------
  // tool-turn ordering
  // -----------------------------------------------------------------------

  @Test
  void getWindowKeepsToolTurnsInConversationOrder() {
    AssistantMessage toolCall =
        AssistantMessage.builder()
            .content("")
            .toolCalls(List.of(new AssistantMessage.ToolCall("call-1", "function", "lookup", "{}")))
            .build();
    ToolResponseMessage toolResponse =
        ToolResponseMessage.builder()
            .responses(
                List.of(new ToolResponseMessage.ToolResponse("call-1", "lookup", "tool-result")))
            .build();
    AssistantMessage answer = new AssistantMessage("final-answer");

    memory.add(CONVERSATION_ID, List.of(new SystemMessage("rules")));
    memory.add(CONVERSATION_ID, List.of(new UserMessage("question-1")));
    memory.add(CONVERSATION_ID, List.of(toolCall));
    memory.add(CONVERSATION_ID, List.of(toolResponse));
    memory.add(CONVERSATION_ID, List.of(answer));

    List<Message> result = memory.get(CONVERSATION_ID);

    assertThat(result.get(0)).isInstanceOf(SystemMessage.class);
    assertThat(result.subList(1, result.size()))
        .extracting(Message::getMessageType)
        .containsExactly(
            MessageType.USER, MessageType.ASSISTANT, MessageType.TOOL, MessageType.ASSISTANT);
    assertThat(result.get(2)).isSameAs(toolCall);
    assertThat(result.get(3)).isSameAs(toolResponse);
    assertThat(result.get(4)).isSameAs(answer);
  }

  // -----------------------------------------------------------------------
  // full history and rollback
  // -----------------------------------------------------------------------

  @Test
  void windowedReadCanBeRecomputedFromTheRetainedFullHistory() {
    List<Message> messages =
        IntStream.rangeClosed(1, MAX_MESSAGES + 4)
            .mapToObj(i -> (Message) new UserMessage("msg" + i))
            .toList();
    memory.add(CONVERSATION_ID, messages);
    List<Message> firstRead = memory.get(CONVERSATION_ID);

    MessageWindowChatMemory reopened =
        MessageWindowChatMemory.builder()
            .chatMemoryRepository(repository)
            .maxMessages(MAX_MESSAGES)
            .build();

    assertThat(reopened.get(CONVERSATION_ID))
        .extracting(Message::getText)
        .isEqualTo(firstRead.stream().map(Message::getText).toList());
    assertThat(repository.findByConversationId(CONVERSATION_ID)).hasSize(MAX_MESSAGES + 4);
  }

  // -----------------------------------------------------------------------
  // concurrent append/reload
  // -----------------------------------------------------------------------

  @Test
  void concurrentAddsRetainEveryAppendAndStillBoundTheReadWindow() throws Exception {
    AppendableChatMemoryRepository concurrentRepository = new ConcurrentAppendableRepository();
    MessageWindowChatMemory concurrentMemory =
        MessageWindowChatMemory.builder()
            .chatMemoryRepository(concurrentRepository)
            .maxMessages(MAX_MESSAGES)
            .build();
    int appends = MAX_MESSAGES * 4;
    ExecutorService executor = Executors.newFixedThreadPool(8);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(appends);
    try {
      for (int i = 0; i < appends; i++) {
        int index = i;
        executor.execute(
            () -> {
              try {
                start.await();
                concurrentMemory.add(CONVERSATION_ID, List.of(new UserMessage("msg" + index)));
              } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
              } finally {
                done.countDown();
              }
            });
      }
      start.countDown();
      assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
    } finally {
      executor.shutdownNow();
    }

    assertThat(concurrentRepository.findByConversationId(CONVERSATION_ID)).hasSize(appends);
    assertThat(concurrentMemory.get(CONVERSATION_ID))
        .hasSize(MAX_MESSAGES)
        .allMatch(message -> message instanceof UserMessage);
  }

  private static final class ConcurrentAppendableRepository
      implements AppendableChatMemoryRepository {

    private final Map<String, List<Message>> store = new ConcurrentHashMap<>();

    @Override
    public List<String> findConversationIds() {
      return List.copyOf(store.keySet());
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
      return List.copyOf(store.getOrDefault(conversationId, List.of()));
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
      store.put(conversationId, new CopyOnWriteArrayList<>(messages));
    }

    @Override
    public void appendAll(String conversationId, List<Message> messages) {
      store
          .computeIfAbsent(conversationId, ignored -> new CopyOnWriteArrayList<>())
          .addAll(messages);
    }

    @Override
    public void deleteByConversationId(String conversationId) {
      store.remove(conversationId);
    }
  }
}
