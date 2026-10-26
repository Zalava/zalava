package org.zalava.capabilities.operation.application;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.zalava.api.InvocationContext;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.ToolKey;
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
          "instance-global",
          "disabled",
          "incompatible",
          "integrity-unverified",
          "untrusted");

  private final MemberToolAccess access;

  public MemberProviderCapabilityPolicy() {
    this(null);
  }

  public MemberProviderCapabilityPolicy(MemberToolAccess access) {
    this.access = access;
  }

  public static boolean eligible(ZalavaProvider provider, ZalavaToolDescriptor tool) {
    Set<String> tags =
        Stream.concat(provider.descriptor().policyTags().stream(), tool.policyTags().stream())
            .map(value -> value.strip().toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    return tags.contains("zalava_backed")
        && provider.descriptor().scope() != null
        && !provider.descriptor().scope().isEmpty()
        && tags.stream().noneMatch(BLOCKED::contains);
  }

  public void requireAllowed(
      ZalavaProvider provider, ZalavaToolDescriptor tool, InvocationContext context) {
    if (!"MEMBER".equals(context.attributes().get("accountRole"))) return;
    Set<String> tags =
        Stream.concat(provider.descriptor().policyTags().stream(), tool.policyTags().stream())
            .map(value -> value.strip().toLowerCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    boolean allowed =
        tags.contains("zalava_backed")
            && (access == null
                ? tags.contains(MEMBER_SAFE)
                : access.allowed(
                    context.actorId(),
                    new ToolKey(
                        provider.descriptor().moduleId(),
                        provider.descriptor().providerId(),
                        tool.name())))
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
