package org.zalava.discovery.application;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.zalava.discovery.RemoteModuleCandidate;

/** SEA-owned policy gate applied to remote candidates before any ranking or classification. */
public final class RemoteCandidatePolicy {

  public static final Set<String> DEFAULT_BLOCKED_PERMISSIONS =
      Set.of(
          "shell",
          "broad-access",
          "unrestricted-host",
          "host-filesystem-unrestricted",
          "credentials");

  private final Set<String> blockedPermissions;

  public RemoteCandidatePolicy(Set<String> blockedPermissions) {
    this.blockedPermissions = Set.copyOf(blockedPermissions);
  }

  public RemoteCandidatePolicy() {
    this(DEFAULT_BLOCKED_PERMISSIONS);
  }

  public boolean eligible(RemoteModuleCandidate candidate) {
    List<String> permissions = candidate.permissions();
    return permissions == null
        || permissions.stream()
            .map(RemoteCandidatePolicy::normalize)
            .noneMatch(blockedPermissions::contains);
  }

  private static String normalize(String permission) {
    return permission == null ? "" : permission.trim().toLowerCase(Locale.ROOT);
  }
}
