package org.zalava.discovery.adapter.out.springai;

import java.util.List;
import java.util.Map;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.zalava.ProviderDescriptor;
import org.zalava.ZalavaToolDescriptor;
import org.zalava.discovery.application.port.in.ToolDiscovery;

public final class SeaToolReferences {

  private SeaToolReferences() {}

  public static ToolReference from(ToolDiscovery.ToolMatch match) {
    return ToolReference.builder()
        .toolName(SeaToolCallbackNames.forTool(match.providerId(), match.toolName()))
        .summary(summary(match))
        .build();
  }

  public static ToolReference from(ProviderDescriptor provider, ZalavaToolDescriptor tool) {
    return ToolReference.builder()
        .toolName(SeaToolCallbackNames.forTool(provider.providerId(), tool.name()))
        .summary(
            summary(
                provider.providerId(),
                provider.displayName(),
                tool.name(),
                tool.description(),
                tool.sideEffecting(),
                tool.policyTags(),
                provider.policyTags(),
                provider.scope()))
        .build();
  }

  private static String summary(ToolDiscovery.ToolMatch match) {
    return summary(
        match.providerId(),
        match.providerDisplayName(),
        match.toolName(),
        match.description(),
        match.sideEffecting(),
        match.policyTags(),
        match.providerPolicyTags(),
        match.scope());
  }

  private static String summary(
      String providerId,
      String providerDisplayName,
      String toolName,
      String description,
      boolean sideEffecting,
      List<String> policyTags,
      List<String> providerPolicyTags,
      Map<String, String> providerScope) {
    String scope =
        providerScope.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + "=" + entry.getValue())
            .reduce((left, right) -> left + "," + right)
            .orElse("none");
    String policies =
        policyTags.stream().sorted().reduce((left, right) -> left + "," + right).orElse("none");
    String providerPolicies =
        providerPolicyTags.stream()
            .sorted()
            .reduce((left, right) -> left + "," + right)
            .orElse("none");
    return "%s: %s [providerId=%s, toolName=%s, sideEffecting=%s, policyTags=%s, providerPolicyTags=%s, scope=%s]"
        .formatted(
            providerDisplayName,
            description,
            providerId,
            toolName,
            sideEffecting,
            policies,
            providerPolicies,
            scope);
  }
}
