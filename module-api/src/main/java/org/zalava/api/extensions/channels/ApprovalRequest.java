package org.zalava.api.extensions.channels;

import java.util.Objects;

/** Binding that Core rechecks before recording an approve or deny action. */
public record ApprovalRequest(
    String requestId,
    String actorId,
    ApprovalOperation operation,
    String prompt,
    String approveActionId,
    String denyActionId) {
  public ApprovalRequest {
    ChannelValues.requireNonBlank(requestId, "requestId");
    ChannelValues.requireNonBlank(actorId, "actorId");
    operation = Objects.requireNonNull(operation, "operation must not be null");
    ChannelValues.requireNonBlank(prompt, "prompt");
    ChannelValues.requireNonBlank(approveActionId, "approveActionId");
    ChannelValues.requireNonBlank(denyActionId, "denyActionId");
    if (approveActionId.equals(denyActionId)) {
      throw new IllegalArgumentException("approval actions must differ");
    }
  }
}
