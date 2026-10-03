package org.zalava.web.ui.protocol;

import java.util.LinkedHashMap;
import java.util.Map;

/** JSON wire representation for a {@link UiEvent}; clients never infer authority from events. */
public final class UiEventJson {
  private UiEventJson() {}

  public static Map<String, Object> from(UiEvent event) {
    Map<String, Object> json = new LinkedHashMap<>();
    json.put("protocol", event.protocolVersion());
    switch (event) {
      case UiEvent.ConversationList conversations -> {
        json.put("type", "conversation.list");
        json.put("conversationIds", conversations.conversationIds());
      }
      case UiEvent.ConversationSnapshot snapshot -> {
        json.put("type", "conversation.snapshot");
        json.put("conversationId", snapshot.conversationId());
        json.put("messages", snapshot.messages());
        json.put("channelId", snapshot.channelId());
        json.put("canSend", snapshot.canSend());
        json.put("canContinue", snapshot.canContinue());
      }
      case UiEvent.ChatDelta delta -> {
        json.put("type", "chat.delta");
        json.put("conversationId", delta.conversationId());
        json.put("text", delta.text());
      }
      case UiEvent.ChatCompleted completed -> {
        json.put("type", "chat.completed");
        json.put("conversationId", completed.conversationId());
        json.put("text", completed.text());
        json.put("jobIds", completed.jobIds());
      }
      case UiEvent.ToolProgress progress -> {
        json.put("type", "tool.progress");
        json.put("invocationId", progress.invocationId());
        json.put("label", progress.label());
        json.put("percent", progress.percent());
      }
      case UiEvent.ToolResult result -> {
        json.put("type", "tool.result");
        json.put("invocationId", result.invocationId());
        json.put("summary", result.summary());
      }
      case UiEvent.PermissionRequested request -> {
        json.put("type", "permission.requested");
        json.put("jobId", request.jobId());
        json.put("requestId", request.requestId());
        json.put("summary", request.summary());
      }
      case UiEvent.ArtifactAvailable artifact -> {
        json.put("type", "artifact.available");
        json.put("artifactId", artifact.artifactId());
        json.put("name", artifact.name());
      }
      case UiEvent.AttachmentAvailable attachment -> {
        json.put("type", "attachment.available");
        if (attachment.attachmentId() != null) {
          json.put("attachmentId", attachment.attachmentId());
        }
        if (attachment.sourceId() != null) {
          json.put("sourceId", attachment.sourceId());
        }
        json.put("intent", attachment.intent());
        json.put("name", attachment.name());
        json.put("contentType", attachment.contentType());
        json.put("byteCount", attachment.byteCount());
        json.put("sha256", attachment.sha256());
      }
      case UiEvent.AttachmentRemoved removed -> {
        json.put("type", "attachment.removed");
        json.put("attachmentId", removed.attachmentId());
      }
      case UiEvent.JobUpdated job -> {
        json.put("type", "job.updated");
        json.put("jobId", job.jobId());
        json.put("status", job.status());
        json.put("summary", job.summary());
      }
      case UiEvent.Failure failure -> {
        json.put("type", "failure");
        json.put("operation", failure.operation());
        json.put("message", failure.message());
      }
      case UiEvent.Cancelled cancelled -> {
        json.put("type", "cancelled");
        json.put("operationId", cancelled.operationId());
        json.put("reason", cancelled.reason());
      }
    }
    return Map.copyOf(json);
  }
}
