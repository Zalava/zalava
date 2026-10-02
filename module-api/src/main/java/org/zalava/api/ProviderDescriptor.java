package org.zalava.api;

import java.util.List;
import java.util.Map;

public record ProviderDescriptor(
    String providerId,
    String moduleId,
    String providerType,
    String displayName,
    String description,
    String version,
    ProviderCapabilities capabilities,
    List<String> policyTags,
    Map<String, String> scope) {

  public ProviderDescriptor {
    policyTags = policyTags == null ? List.of() : List.copyOf(policyTags);
    scope = scope == null ? Map.of() : Map.copyOf(scope);
  }
}
