package org.zalava.api;

public record ProviderCapabilities(
    boolean supportsTools,
    boolean supportsResources,
    boolean supportsPrompts,
    boolean supportsResourceTemplates,
    boolean supportsSubscriptions,
    boolean supportsNotifications,
    boolean supportsStructuredToolOutput,
    boolean supportsStreaming) {

  public static ProviderCapabilities toolsOnly() {
    return new ProviderCapabilities(true, false, false, false, false, false, false, false);
  }
}
