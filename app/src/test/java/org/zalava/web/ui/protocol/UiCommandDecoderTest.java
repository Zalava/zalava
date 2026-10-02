package org.zalava.web.ui.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UiCommandDecoderTest {
  @Test
  void decodesVersionedChatIntentWithoutClientSuppliedAuthority() {
    var command =
        UiCommandDecoder.decode(
            Map.of(
                "protocol",
                UiCommandDecoder.VERSION,
                "type",
                "chat.send",
                "conversationId",
                " conversation-1 ",
                "message",
                " hello ",
                "actor",
                "admin",
                "permission",
                "allow"));

    assertThat(command)
        .contains(
            new UiCommand.SendChat(UiCommandDecoder.VERSION, "conversation-1", "hello", List.of()));
  }

  @Test
  void rejectsUnknownVersionsMalformedAndUnknownCommands() {
    assertThat(UiCommandDecoder.decode(Map.of("protocol", "sea.ui/v2", "type", "chat.create")))
        .isEmpty();
    assertThat(UiCommandDecoder.decode(Map.of("type", "chat.send", "message", "hello"))).isEmpty();
    assertThat(UiCommandDecoder.decode(Map.of("type", "permission.grant", "requestId", "p-1")))
        .isEmpty();
  }

  @Test
  void acceptsLegacyWireNamesDuringTheMigration() {
    assertThat(
            UiCommandDecoder.decode(
                Map.of("type", "userMessage", "conversationId", "web", "message", "hello")))
        .contains(new UiCommand.SendChat(UiCommandDecoder.VERSION, "web", "hello", List.of()));
  }

  @Test
  void decodesBoundedAttachmentIntentAndIds() {
    assertThat(
            UiCommandDecoder.decode(
                Map.of(
                    "type", "attachment.put",
                    "intent", "task-only",
                    "name", "notes.txt",
                    "contentType", "text/plain",
                    "content", "aGVsbG8=")))
        .contains(
            new UiCommand.PutAttachment(
                UiCommandDecoder.VERSION, "task-only", "notes.txt", "text/plain", "aGVsbG8="));
    assertThat(UiCommandDecoder.decode(Map.of("type", "attachment.delete", "attachmentId", "a-1")))
        .contains(new UiCommand.DeleteAttachment(UiCommandDecoder.VERSION, "a-1"));
    assertThat(
            UiCommandDecoder.decode(
                Map.of(
                    "type",
                    "chat.send",
                    "conversationId",
                    "chat-1",
                    "message",
                    "hi",
                    "attachmentIds",
                    List.of("a-1"))))
        .contains(new UiCommand.SendChat(UiCommandDecoder.VERSION, "chat-1", "hi", List.of("a-1")));
  }

  @Test
  void rejectsUnsupportedAttachmentIntentAndMalformedIds() {
    assertThat(
            UiCommandDecoder.decode(
                Map.of(
                    "type", "attachment.put",
                    "intent", "durable",
                    "name", "notes.txt",
                    "contentType", "text/plain",
                    "content", "aGVsbG8=")))
        .isEmpty();
    assertThat(UiCommandDecoder.decode(Map.of("type", "attachment.delete"))).isEmpty();
    assertThat(
            UiCommandDecoder.decode(
                Map.of(
                    "type",
                    "chat.send",
                    "conversationId",
                    "chat-1",
                    "message",
                    "hi",
                    "attachmentIds",
                    List.of(42))))
        .isEmpty();
  }
}
