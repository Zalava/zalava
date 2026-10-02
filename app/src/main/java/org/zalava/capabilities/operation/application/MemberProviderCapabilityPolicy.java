package org.zalava.capabilities.operation.application;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.zalava.InvocationContext;
import org.zalava.ZalavaProvider;
import org.zalava.ZalavaToolDescriptor;
import org.zalava.capabilities.operation.application.port.in.ProviderToolOperationException;

/** Default-deny provider capability baseline for authenticated member execution. */
public final class MemberProviderCapabilityPolicy {
  public static final String MEMBER_SAFE = "member-safe";
  private static final Set<String> BLOCKED =
      Set.of(
          "legacy",
          "legacy_enabled",
          "broad-access",
          "shell",
          "unrestricted-host",
          "admin",
          "instance-global");

  public void requireAllowed(
      ZalavaProvider provider, ZalavaToolDescriptor tool, InvocationContext context) {
    if (!"MEMBER".equals(context.attributes().get("accountRole"))) return;
    Set<String> tags =
        Stream.concat(provider.descriptor().policyTags().stream(), tool.policyTags().stream())
            .map(value -> value.strip().toLowerCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    boolean allowed =
        tags.contains("sea_backed")
            && tags.contains(MEMBER_SAFE)
            && provider.descriptor().scope() != null
            && !provider.descriptor().scope().isEmpty()
            && tags.stream().noneMatch(BLOCKED::contains);
    if (!allowed) {
      throw new ProviderToolOperationException(
          ProviderToolOperationException.Code.UNSUPPORTED,
          "Member capability policy denied provider operation "
              + provider.descriptor().providerId()
              + "/"
              + tool.name());
    }
  }
}
