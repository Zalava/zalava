package org.zalava.channels.domain;

public record ChannelApproval(
    String requestId, String providerId, String toolName, String taskReference, Decision decision) {
  public enum Decision {
    PENDING,
    ALLOWED,
    DENIED,
    REVOKED
  }

  public boolean isTaskScoped() {
    return taskReference != null && !taskReference.isBlank();
  }
}
