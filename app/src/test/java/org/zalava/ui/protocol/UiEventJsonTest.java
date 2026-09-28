package org.zalava.ui.protocol;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class UiEventJsonTest {
  @Test
  void serializesEverySupportedObservableEvent() {
    List<UiEvent> events =
        List.of(
            new UiEvent.ConversationList(UiCommandDecoder.VERSION, List.of("chat")),
            new UiEvent.ConversationSnapshot(
                UiCommandDecoder.VERSION,
                "chat",
                List.of(new UiEvent.ConversationMessage("user", "hello"))),
            new UiEvent.ChatDelta(UiCommandDecoder.VERSION, "chat", "hello"),
            new UiEvent.ChatCompleted(UiCommandDecoder.VERSION, "chat", "done", List.of("job")),
            new UiEvent.ToolProgress(UiCommandDecoder.VERSION, "tool", "Working", 50),
            new UiEvent.ToolResult(UiCommandDecoder.VERSION, "tool", "done"),
            new UiEvent.PermissionRequested(
                UiCommandDecoder.VERSION, "job", "permission", "Allow?"),
            new UiEvent.ArtifactAvailable(UiCommandDecoder.VERSION, "artifact", "report"),
            new UiEvent.AttachmentAvailable(
                UiCommandDecoder.VERSION,
                "attachment",
                "source",
                "knowledge-import",
                "report.txt",
                "text/plain",
                12,
                "sha"),
            new UiEvent.AttachmentRemoved(UiCommandDecoder.VERSION, "attachment"),
            new UiEvent.JobUpdated(UiCommandDecoder.VERSION, "job", "RUNNING", "Working"),
            new UiEvent.Failure(UiCommandDecoder.VERSION, "chat.send", "Failed"),
            new UiEvent.Cancelled(UiCommandDecoder.VERSION, "job", "Cancelled"));

    assertThat(events)
        .allSatisfy(
            event -> {
              var json = UiEventJson.from(event);
              assertThat(json)
                  .containsEntry("protocol", UiCommandDecoder.VERSION)
                  .containsKey("type");
            });
  }
}
