package org.zalava.chat.adapter.out.springai;

import java.util.List;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.zalava.chat.application.port.out.ChatConversationStore;
import org.zalava.chat.domain.ChatMessage;

/** Compatibility adapter for callers still supplying Spring AI's legacy repository. */
public final class SpringAiChatConversationStore implements ChatConversationStore {
  private final ChatMemoryRepository repository;

  public SpringAiChatConversationStore(ChatMemoryRepository repository) {
    this.repository = repository;
  }

  @Override
  public List<String> findConversationIds() {
    return repository.findConversationIds();
  }

  @Override
  public List<ChatMessage> findByConversationId(String id) {
    return repository.findByConversationId(id).stream()
        .map(message -> new ChatMessage(role(message), message.getText()))
        .toList();
  }

  @Override
  public void saveEmptyConversation(String id) {
    repository.saveAll(id, List.of());
  }

  private static ChatMessage.Role role(Message message) {
    return switch (message.getMessageType()) {
      case USER -> ChatMessage.Role.USER;
      case ASSISTANT -> ChatMessage.Role.ASSISTANT;
      case SYSTEM -> ChatMessage.Role.SYSTEM;
      default ->
          throw new IllegalArgumentException(
              "Unsupported persisted chat message: " + message.getMessageType());
    };
  }
}
