package org.zalava.web.ui.protocol;

import java.util.List;

/**
 * Transport-neutral state sent by SEA to a UI. Event data is observable state only; it never grants
 * a permission or authorizes a client-side action.
 */
public sealed interface UiEvent
    permits UiEvent.ConversationList,
        UiEvent.ConversationSnapshot,
        UiEvent.ChatDelta,
        UiEvent.ChatCompleted,
        UiEvent.ToolProgress,
        UiEvent.ToolResult,
        UiEvent.PermissionRequested,
        UiEvent.ArtifactAvailable,
        UiEvent.AttachmentAvailable,
        UiEvent.AttachmentRemoved,
        UiEvent.JobUpdated,
        UiEvent.Failure,
        UiEvent.Cancelled {

  String protocolVersion();

  record ConversationList(String protocolVersion, List<String> conversationIds) implements UiEvent {
    public ConversationList {
      conversationIds = List.copyOf(conversationIds);
    }
  }

  record ConversationSnapshot(
      String protocolVersion, String conversationId, List<ConversationMessage> messages)
      implements UiEvent {
    public ConversationSnapshot {
      messages = List.copyOf(messages);
    }
  }

  record ConversationMessage(String role, String text) {}

  record ChatDelta(String protocolVersion, String conversationId, String text) implements UiEvent {}

  record ChatCompleted(
      String protocolVersion, String conversationId, String text, List<String> jobIds)
      implements UiEvent {
    public ChatCompleted {
      jobIds = List.copyOf(jobIds);
    }
  }

  record ToolProgress(String protocolVersion, String invocationId, String label, int percent)
      implements UiEvent {}

  record ToolResult(String protocolVersion, String invocationId, String summary)
      implements UiEvent {}

  record PermissionRequested(String protocolVersion, String jobId, String requestId, String summary)
      implements UiEvent {}

  record ArtifactAvailable(String protocolVersion, String artifactId, String name)
      implements UiEvent {}

  /** A server-issued, owner-scoped attachment reference; never a client-supplied path or owner. */
  record AttachmentAvailable(
      String protocolVersion,
      String attachmentId,
      String sourceId,
      String intent,
      String name,
      String contentType,
      long byteCount,
      String sha256)
      implements UiEvent {}

  record AttachmentRemoved(String protocolVersion, String attachmentId) implements UiEvent {}

  record JobUpdated(String protocolVersion, String jobId, String status, String summary)
      implements UiEvent {}

  record Failure(String protocolVersion, String operation, String message) implements UiEvent {}

  record Cancelled(String protocolVersion, String operationId, String reason) implements UiEvent {}
}
