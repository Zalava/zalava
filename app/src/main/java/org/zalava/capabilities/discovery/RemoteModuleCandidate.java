package org.zalava.capabilities.discovery;

import java.util.List;

/** Compact, read-only remote module metadata. It is never downloaded, installed or activated. */
public record RemoteModuleCandidate(
    String moduleId,
    String version,
    String digest,
    String displayName,
    String description,
    List<String> permissions) {

  public RemoteModuleCandidate {
    moduleId = requireText(moduleId, "moduleId");
    version = requireText(version, "version");
    digest = requireText(digest, "digest");
    displayName = displayName == null ? "" : displayName;
    description = description == null ? "" : description;
    permissions = permissions == null ? List.of() : List.copyOf(permissions);
  }

  private static String requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Remote module candidate " + field + " must not be blank");
    }
    return value;
  }
}
