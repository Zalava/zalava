package org.zalava.assistant.conversation.adapter.out.springai;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.zalava.assistant.conversation.application.port.in.ActorConversations;
import org.zalava.assistant.conversation.application.port.in.ConversationRepository;
import org.zalava.assistant.conversation.domain.ActorConversationId;
import org.zalava.assistant.conversation.domain.ConversationMessage;

public final class SpringAiChatMemoryRepository implements AppendableChatMemoryRepository {
  private final ConversationRepository conversations;
  private final ActorConversations actorConversations;

  public SpringAiChatMemoryRepository(ConversationRepository conversations) {
    this(conversations, null);
  }

  public SpringAiChatMemoryRepository(
      ConversationRepository conversations, ActorConversations actorConversations) {
    this.conversations = conversations;
    this.actorConversations = actorConversations;
  }

  @Override
  public List<String> findConversationIds() {
    return conversations.findConversationIds();
  }

  @Override
  public List<Message> findByConversationId(String conversationId) {
    return conversationMessages(conversationId).stream()
        .map(SpringAiChatMemoryRepository::message)
        .toList();
  }

  @Override
  public void appendAll(String conversationId, List<Message> messages) {
    List<ConversationMessage> converted = messages(messages);
    actorConversation(conversationId)
        .ifPresentOrElse(
            actor -> actorConversations.appendAll(actor.actor(), actor.reference(), converted),
            () -> conversations.appendAll(conversationId, converted));
  }

  @Override
  public void saveAll(String conversationId, List<Message> messages) {
    List<ConversationMessage> converted = messages(messages);
    actorConversation(conversationId)
        .ifPresentOrElse(
            actor -> actorConversations.saveAll(actor.actor(), actor.reference(), converted),
            () -> conversations.saveAll(conversationId, converted));
  }

  @Override
  public void deleteByConversationId(String conversationId) {
    actorConversation(conversationId)
        .ifPresentOrElse(
            actor -> actorConversations.delete(actor.actor(), actor.reference()),
            () -> conversations.deleteByConversationId(conversationId));
  }

  private List<ConversationMessage> conversationMessages(String conversationId) {
    return actorConversation(conversationId)
        .map(actor -> actorConversations.findByReference(actor.actor(), actor.reference()))
        .orElseGet(() -> conversations.findByConversationId(conversationId));
  }

  private Optional<ActorConversationId> actorConversation(String conversationId) {
    if (actorConversations == null) return Optional.empty();
    try {
      return Optional.of(ActorConversationId.parse(conversationId));
    } catch (IllegalArgumentException ignored) {
      return Optional.empty();
    }
  }

  private static List<ConversationMessage> messages(List<Message> messages) {
    return messages.stream()
        .filter(message -> message.getText() != null && !message.getText().isBlank())
        .map(SpringAiChatMemoryRepository::conversationMessage)
        .toList();
  }

  private static ConversationMessage conversationMessage(Message message) {
    return new ConversationMessage(
        switch (message.getMessageType()) {
          case USER -> ConversationMessage.Role.USER;
          case ASSISTANT -> ConversationMessage.Role.ASSISTANT;
          case SYSTEM -> ConversationMessage.Role.SYSTEM;
          default ->
              throw new IllegalArgumentException(
                  "Unsupported persisted chat message: " + message.getMessageType());
        },
        message.getText(),
        attachmentNames(message));
  }

  private static List<String> attachmentNames(Message message) {
    Object value = message.getMetadata().get("zalava.attachmentNames");
    if (!(value instanceof List<?> names)) return List.of();
    return names.stream().map(String.class::cast).toList();
  }

  private static Message message(ConversationMessage message) {
    return switch (message.role()) {
      case USER ->
          message.attachmentNames().isEmpty()
              ? new UserMessage(message.text())
              : UserMessage.builder()
                  .text(message.text())
                  .metadata(Map.of("zalava.attachmentNames", message.attachmentNames()))
                  .build();
      case ASSISTANT -> new AssistantMessage(message.text());
      case SYSTEM -> new SystemMessage(message.text());
    };
  }
}
