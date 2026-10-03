package org.zalava.assistant.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.zalava.capabilities.discovery.application.port.in.ToolDiscovery;
import org.zalava.identity.accounts.domain.AccountRole;

public final class DynamicToolActivationPolicy {

  private static final String ZALAVA_BACKED = "zalava_backed";
  private static final Set<String> BLOCKED_TAGS =
      Set.of(
          "legacy",
          "legacy_enabled",
          "disabled",
          "broad-access",
          "shell",
          "unrestricted-host",
          "incompatible",
          "integrity-unverified",
          "untrusted");

  public ActivationDecision evaluate(ToolDiscovery.ToolMatch match) {
    return evaluate(match, null);
  }

  public ActivationDecision evaluate(ToolDiscovery.ToolMatch match, AccountRole role) {
    List<String> providerTags = normalize(match.providerPolicyTags());
    List<String> toolTags = normalize(match.policyTags());
    List<String> combinedTags =
        Stream.concat(providerTags.stream(), toolTags.stream()).distinct().toList();
    List<String> rejections = new ArrayList<>();
    List<String> notes = new ArrayList<>();

    if (!combinedTags.contains(ZALAVA_BACKED)) {
      rejections.add("missing Zalava-backed trust marker");
    }
    List<String> blocked = combinedTags.stream().filter(BLOCKED_TAGS::contains).sorted().toList();
    if (!blocked.isEmpty()) {
      rejections.add("blocked policy tags: " + String.join(",", blocked));
    }
    if (role == AccountRole.MEMBER && !combinedTags.contains("member-safe")) {
      rejections.add("missing explicit member-safe capability marker");
    }
    if (role == AccountRole.MEMBER && match.scope().isEmpty()) {
      rejections.add("missing provider scope for member capability");
    }
    notes.add("permission boundary: provider=" + match.providerId());
    notes.add("compatibility boundary: provider-scoped callback");
    notes.add("integrity boundary: Zalava-backed installed provider metadata");
    notes.add("audit boundary: ProviderToolOperations observation");
    if (match.sideEffecting()) {
      notes.add("approval boundary: side-effecting calls require execution approval");
    }
    return new ActivationDecision(rejections.isEmpty(), rejections, notes);
  }

  public boolean allows(ToolDiscovery.ToolMatch match) {
    return evaluate(match).allowed();
  }

  public boolean allows(ToolDiscovery.ToolMatch match, AccountRole role) {
    return evaluate(match, role).allowed();
  }

  private static List<String> normalize(List<String> tags) {
    return tags.stream()
        .map(tag -> tag.toLowerCase(Locale.ROOT).strip())
        .filter(tag -> !tag.isBlank())
        .distinct()
        .toList();
  }

  public record ActivationDecision(boolean allowed, List<String> rejections, List<String> notes) {
    public ActivationDecision {
      rejections = List.copyOf(rejections);
      notes = List.copyOf(notes);
    }
  }
}
