package org.zalava.web.ui.protocol;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Validates UI input before a transport dispatches it to SEA application ports. */
public final class UiCommandDecoder {
  public static final String VERSION = "sea.ui/v1";
  private static final int MAXIMUM_ATTACHMENTS = 5;

  private UiCommandDecoder() {}

  /**
   * Decodes the first version of the protocol and the equivalent legacy WebSocket command names.
   * Invalid input is rejected before it can reach a use case.
   */
  public static Optional<UiCommand> decode(Map<String, ?> payload) {
    if (payload == null) return Optional.empty();
    String type = string(payload.get("type"));
    if (type == null) return Optional.empty();
    String version = string(payload.get("protocol"));
    if (version != null && !VERSION.equals(version)) return Optional.empty();
    String acceptedVersion = version == null ? VERSION : version;
    return switch (type) {
      case "chat.send", "userMessage" -> sendChat(payload, acceptedVersion);
      case "chat.select", "channelChanged" -> selectConversation(payload, acceptedVersion);
      case "chat.create", "createConversation" ->
          Optional.of(new UiCommand.CreateConversation(acceptedVersion));
      case "approval.decide" -> decideApproval(payload, acceptedVersion);
      case "attachment.put" -> putAttachment(payload, acceptedVersion);
      case "attachment.delete" -> deleteAttachment(payload, acceptedVersion);
      default -> Optional.empty();
    };
  }

  private static Optional<UiCommand> sendChat(Map<String, ?> payload, String version) {
    String conversationId = string(payload.get("conversationId"));
    String text = string(payload.get("message"));
    if (blank(conversationId) || blank(text)) return Optional.empty();
    Optional<List<String>> attachmentIds = attachmentIds(payload.get("attachmentIds"));
    if (attachmentIds.isEmpty()) return Optional.empty();
    return Optional.of(
        new UiCommand.SendChat(version, conversationId.trim(), text.trim(), attachmentIds.get()));
  }

  private static Optional<UiCommand> selectConversation(Map<String, ?> payload, String version) {
    String conversationId = string(payload.get("conversationId"));
    if (blank(conversationId)) return Optional.empty();
    return Optional.of(new UiCommand.SelectConversation(version, conversationId.trim()));
  }

  private static Optional<UiCommand> decideApproval(Map<String, ?> payload, String version) {
    String jobId = string(payload.get("jobId"));
    String requestId = string(payload.get("requestId"));
    String decision = string(payload.get("decision"));
    if (blank(jobId) || blank(requestId) || blank(decision)) return Optional.empty();
    return switch (decision) {
      case "allow-once" ->
          Optional.of(
              new UiCommand.DecideApproval(
                  version, jobId, requestId, UiCommand.DecideApproval.Decision.ALLOW_ONCE));
      case "deny" ->
          Optional.of(
              new UiCommand.DecideApproval(
                  version, jobId, requestId, UiCommand.DecideApproval.Decision.DENY));
      default -> Optional.empty();
    };
  }

  private static Optional<UiCommand> putAttachment(Map<String, ?> payload, String version) {
    String intent = string(payload.get("intent"));
    String name = string(payload.get("name"));
    String contentType = string(payload.get("contentType"));
    String content = string(payload.get("content"));
    if (blank(intent) || blank(name) || blank(contentType) || blank(content)) {
      return Optional.empty();
    }
    if (!intent.equals("task-only") && !intent.equals("knowledge-import")) {
      return Optional.empty();
    }
    return Optional.of(
        new UiCommand.PutAttachment(version, intent, name.trim(), contentType.trim(), content));
  }

  private static Optional<UiCommand> deleteAttachment(Map<String, ?> payload, String version) {
    String attachmentId = string(payload.get("attachmentId"));
    if (blank(attachmentId)) return Optional.empty();
    return Optional.of(new UiCommand.DeleteAttachment(version, attachmentId.trim()));
  }

  private static Optional<List<String>> attachmentIds(Object value) {
    if (value == null) return Optional.of(List.of());
    if (!(value instanceof List<?> list) || list.size() > MAXIMUM_ATTACHMENTS) {
      return Optional.empty();
    }
    List<String> ids = new ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof String text) || blank(text)) return Optional.empty();
      ids.add(text.trim());
    }
    return Optional.of(List.copyOf(ids));
  }

  private static String string(Object value) {
    return value instanceof String text ? text : null;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
