package org.zalava.agent;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConversationChannelContextTest {

  @Test
  void restoresOuterChannelAfterNestedInvocation() {
    ConversationChannelContext.call(
        "telegram-123",
        () -> {
          assertThat(ConversationChannelContext.current()).contains("telegram");

          ConversationChannelContext.call(
              "web-456", () -> assertThat(ConversationChannelContext.current()).contains("web"));

          assertThat(ConversationChannelContext.current()).contains("telegram");
          return null;
        });

    assertThat(ConversationChannelContext.current()).isEmpty();
  }

  @Test
  void treatsNonTelegramAndMissingConversationIdsAsWeb() {
    ConversationChannelContext.call(
        "web-456", () -> assertThat(ConversationChannelContext.current()).contains("web"));
    ConversationChannelContext.call(
        null, () -> assertThat(ConversationChannelContext.current()).contains("web"));
  }
}
