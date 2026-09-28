package org.zalava.development;

import java.util.Map;

/** Immutable evidence for one materialized, user-owned development workspace. */
public record DevelopmentWorkspace(
    DevelopmentRequestId requestId, String root, Map<String, String> sha256) {
  public DevelopmentWorkspace {
    if (requestId == null) throw new IllegalArgumentException("requestId must not be null");
    if (root == null || root.isBlank())
      throw new IllegalArgumentException("workspace root must not be blank");
    sha256 = sha256 == null ? Map.of() : Map.copyOf(sha256);
  }
}
