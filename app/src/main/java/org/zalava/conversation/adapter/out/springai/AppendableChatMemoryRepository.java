package org.zalava.conversation.adapter.out.springai;

import java.util.List;
import org.springframework.ai.chat.messages.Message;

/**
 * SEA compatibility extension of Spring AI's {@link
 * org.springframework.ai.chat.memory.ChatMemoryRepository} adding an append-only write.
 *
 * <p>Missing upstream equivalent: the maintained {@code ChatMemoryRepository} exposes only {@code
 * saveAll}, and Spring AI's {@code MessageWindowChatMemory} uses it to replace a conversation with
 * an already-trimmed window. SEA must retain the complete history for rollback and audit and apply
 * the window only when reading, so it needs an explicit append operation.
 */
public interface AppendableChatMemoryRepository
    extends org.springframework.ai.chat.memory.ChatMemoryRepository {

  void appendAll(String conversationId, List<Message> messages);
}
