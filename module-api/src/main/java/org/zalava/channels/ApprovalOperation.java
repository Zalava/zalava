package org.zalava.channels;

import java.util.Map;

/** SEA-authored operation pending a semantic approval, not model-generated message text. */
public record ApprovalOperation(String operationId, String target, Map<String, String> arguments) {
  public ApprovalOperation {
    ChannelValues.requireNonBlank(operationId, "operationId");
    ChannelValues.requireNonBlank(target, "target");
    arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
  }
}
