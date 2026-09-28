package org.zalava.ui.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UiProtocolCoverageTest {
  @Test
  void decodesEverySupportedCommandAndRejectsNonStringRequiredFields() {
    assertThat(UiCommandDecoder.decode(null)).isEmpty();
    assertThat(UiCommandDecoder.decode(Map.of())).isEmpty();
    assertThat(UiCommandDecoder.decode(Map.of("type", 42))).isEmpty();
    assertThat(UiCommandDecoder.decode(Map.of("type", "chat.create")))
        .contains(new UiCommand.CreateConversation(UiCommandDecoder.VERSION));
    assertThat(UiCommandDecoder.decode(Map.of("type", "createConversation")))
        .contains(new UiCommand.CreateConversation(UiCommandDecoder.VERSION));
    assertThat(UiCommandDecoder.decode(Map.of("type", "chat.select", "conversationId", "chat-1")))
        .contains(new UiCommand.SelectConversation(UiCommandDecoder.VERSION, "chat-1"));
    assertThat(
            UiCommandDecoder.decode(Map.of("type", "channelChanged", "conversationId", "chat-1")))
        .contains(new UiCommand.SelectConversation(UiCommandDecoder.VERSION, "chat-1"));
    assertThat(UiCommandDecoder.decode(Map.of("type", "chat.select", "conversationId", 42)))
        .isEmpty();
    assertThat(
            UiCommandDecoder.decode(
                Map.of("type", "chat.send", "conversationId", "chat-1", "message", 42)))
        .isEmpty();
    assertThat(
            UiCommandDecoder.decode(
                Map.of(
                    "type", "attachment.put",
                    "intent", "knowledge-import",
                    "name", "notes.txt",
                    "contentType", "text/plain",
                    "content", "aGVsbG8=")))
        .contains(
            new UiCommand.PutAttachment(
                UiCommandDecoder.VERSION,
                "knowledge-import",
                "notes.txt",
                "text/plain",
                "aGVsbG8="));
    assertThat(
            UiCommandDecoder.decode(
                Map.of("type", "attachment.delete", "attachmentId", "attachment-1")))
        .contains(new UiCommand.DeleteAttachment(UiCommandDecoder.VERSION, "attachment-1"));
  }

  @Test
  void declaresAllObservableServerEventShapes() {
    List<UiEvent> events =
        List.of(
            new UiEvent.ChatDelta(UiCommandDecoder.VERSION, "chat-1", "delta"),
            new UiEvent.ChatCompleted(UiCommandDecoder.VERSION, "chat-1", "done", List.of("job-1")),
            new UiEvent.ToolProgress(UiCommandDecoder.VERSION, "tool-1", "Searching", 50),
            new UiEvent.ToolResult(UiCommandDecoder.VERSION, "tool-1", "Found it"),
            new UiEvent.PermissionRequested(
                UiCommandDecoder.VERSION, "job-1", "permission-1", "Allow write"),
            new UiEvent.ArtifactAvailable(UiCommandDecoder.VERSION, "artifact-1", "report.md"),
            new UiEvent.AttachmentAvailable(
                UiCommandDecoder.VERSION,
                "attachment-1",
                null,
                "task-only",
                "notes.txt",
                "text/plain",
                5,
                "abc"),
            new UiEvent.AttachmentRemoved(UiCommandDecoder.VERSION, "attachment-1"),
            new UiEvent.JobUpdated(UiCommandDecoder.VERSION, "job-1", "RUNNING", "Working"),
            new UiEvent.Failure(UiCommandDecoder.VERSION, "chat.send", "Unavailable"),
            new UiEvent.Cancelled(UiCommandDecoder.VERSION, "job-1", "User cancelled"));

    assertThat(events)
        .allSatisfy(
            event -> assertThat(event.protocolVersion()).isEqualTo(UiCommandDecoder.VERSION));
  }
}
