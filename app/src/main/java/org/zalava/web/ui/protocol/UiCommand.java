package org.zalava.web.ui.protocol;

import java.util.List;

/**
 * A user intent accepted by the Zalava UI boundary.
 *
 * <p>Commands deliberately contain neither an actor nor an authorization decision. The transport
 * resolves the authenticated actor and the application layer remains the authority for ownership,
 * persistence, and policy. Attachment content is bounded transport input; Zalava issues the
 * reference.
 */
public sealed interface UiCommand
    permits UiCommand.SendChat,
        UiCommand.SelectConversation,
        UiCommand.CreateConversation,
        UiCommand.ContinueConversation,
        UiCommand.DecideApproval,
        UiCommand.PutAttachment,
        UiCommand.DeleteAttachment {

  String protocolVersion();

  record SendChat(
      String protocolVersion, String conversationId, String text, List<String> attachmentIds)
      implements UiCommand {
    public SendChat {
      attachmentIds = attachmentIds == null ? List.of() : List.copyOf(attachmentIds);
    }
  }

  record SelectConversation(String protocolVersion, String conversationId) implements UiCommand {}

  record CreateConversation(String protocolVersion) implements UiCommand {}

  record ContinueConversation(String protocolVersion, String conversationId, String destination)
      implements UiCommand {}

  /** A bounded user intent; Zalava resolves the actor, task, request and policy server-side. */
  record DecideApproval(String protocolVersion, String jobId, String requestId, Decision decision)
      implements UiCommand {
    public enum Decision {
      ALLOW_ONCE,
      DENY
    }
  }

  /** Uploads one bounded file with an explicit retention intent. */
  record PutAttachment(
      String protocolVersion, String intent, String name, String contentType, String contentBase64)
      implements UiCommand {}

  record DeleteAttachment(String protocolVersion, String attachmentId) implements UiCommand {}
}
