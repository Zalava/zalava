package org.zalava.conversation.adapter.out.springai;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.zalava.conversation.adapter.out.filesystem.FileSystemConversationStore;
import org.zalava.conversation.application.port.in.ConversationRepository;
import org.zalava.conversation.domain.ConversationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;

class SpringAiChatMemoryRepositoryPersistenceTest {

  @TempDir Path workspace;

  @Test
  void fullHistoryIsRetainedWhileTheReadViewIsWindowed() {
    FileSystemConversationStore store = new FileSystemConversationStore(workspace);
    SpringAiChatMemoryRepository repository = new SpringAiChatMemoryRepository(store);
    MessageWindowChatMemory memory = memory(repository, 3);

    memory.add(
        "web",
        List.of(
            new UserMessage("m1"),
            new UserMessage("m2"),
            new UserMessage("m3"),
            new UserMessage("m4"),
            new UserMessage("m5")));

    assertThat(texts(memory.get("web"))).containsExactly("m3", "m4", "m5");
    assertThat(texts(repository.findByConversationId("web")))
        .containsExactly("m1", "m2", "m3", "m4", "m5");
  }

  @Test
  void restartReconstructsTheSameWindowFromPersistedHistory() {
    SpringAiChatMemoryRepository repository =
        new SpringAiChatMemoryRepository(new FileSystemConversationStore(workspace));
    MessageWindowChatMemory memory = memory(repository, 3);
    memory.add(
        "web",
        List.of(
            new UserMessage("m1"),
            new UserMessage("m2"),
            new UserMessage("m3"),
            new UserMessage("m4")));
    List<String> beforeRestart = texts(memory.get("web"));

    SpringAiChatMemoryRepository reopened =
        new SpringAiChatMemoryRepository(new FileSystemConversationStore(workspace));
    MessageWindowChatMemory reopenedMemory = memory(reopened, 3);

    assertThat(texts(reopenedMemory.get("web"))).isEqualTo(beforeRestart);
    assertThat(texts(reopened.findByConversationId("web"))).containsExactly("m1", "m2", "m3", "m4");
  }

  @Test
  void persistedTextTurnsReloadInConversationOrder() {
    SpringAiChatMemoryRepository repository =
        new SpringAiChatMemoryRepository(new FileSystemConversationStore(workspace));
    MessageWindowChatMemory memory = memory(repository, 10);
    memory.add("web", List.of(new UserMessage("question-1")));
    memory.add("web", List.of(new AssistantMessage("answer-1")));
    memory.add("web", List.of(new UserMessage("question-2")));
    memory.add("web", List.of(new AssistantMessage("answer-2")));

    List<Message> reloaded =
        new SpringAiChatMemoryRepository(new FileSystemConversationStore(workspace))
            .findByConversationId("web");

    assertThat(reloaded)
        .extracting(Message::getMessageType)
        .containsExactly(
            MessageType.USER, MessageType.ASSISTANT, MessageType.USER, MessageType.ASSISTANT);
    assertThat(texts(reloaded)).containsExactly("question-1", "answer-1", "question-2", "answer-2");
  }

  @Test
  void clearActsAsTheRollbackBoundaryForTheRetainedHistory() {
    SpringAiChatMemoryRepository repository =
        new SpringAiChatMemoryRepository(new FileSystemConversationStore(workspace));
    MessageWindowChatMemory memory = memory(repository, 2);
    memory.add("web", List.of(new UserMessage("m1"), new UserMessage("m2"), new UserMessage("m3")));

    memory.clear("web");

    assertThat(memory.get("web")).isEmpty();
    assertThat(repository.findByConversationId("web")).isEmpty();
  }

  @Test
  void concurrentAppendsAreForwardedAndAllReload() throws Exception {
    ConcurrentConversationRepository conversations = new ConcurrentConversationRepository();
    SpringAiChatMemoryRepository repository = new SpringAiChatMemoryRepository(conversations);
    int appends = 64;
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
                repository.appendAll("web", List.of(new UserMessage("m" + index)));
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

    assertThat(repository.findByConversationId("web")).hasSize(appends);
  }

  private static MessageWindowChatMemory memory(
      SpringAiChatMemoryRepository repository, int maxMessages) {
    return MessageWindowChatMemory.builder()
        .chatMemoryRepository(repository)
        .maxMessages(maxMessages)
        .build();
  }

  private static List<String> texts(List<Message> messages) {
    return messages.stream().map(Message::getText).toList();
  }

  private static final class ConcurrentConversationRepository implements ConversationRepository {

    private final Map<String, List<ConversationMessage>> store = new ConcurrentHashMap<>();

    @Override
    public List<String> findConversationIds() {
      return List.copyOf(store.keySet());
    }

    @Override
    public List<ConversationMessage> findByConversationId(String conversationId) {
      return List.copyOf(store.getOrDefault(conversationId, List.of()));
    }

    @Override
    public void appendAll(String conversationId, List<ConversationMessage> messages) {
      store
          .computeIfAbsent(conversationId, ignored -> new CopyOnWriteArrayList<>())
          .addAll(messages);
    }

    @Override
    public void saveAll(String conversationId, List<ConversationMessage> messages) {
      store.put(conversationId, new CopyOnWriteArrayList<>(messages));
    }

    @Override
    public void deleteByConversationId(String conversationId) {
      store.remove(conversationId);
    }
  }
}
