package org.zalava.api;

import java.util.List;
import java.util.Map;

public interface ZalavaProvider extends AutoCloseable {

  ProviderDescriptor descriptor();

  ProviderCapabilities capabilities();

  List<ZalavaToolDescriptor> listTools();

  default ZalavaOperationResult callTool(
      String toolName, Map<String, Object> arguments, InvocationContext context) {
    throw new UnsupportedOperationException(
        "Tool execution is not implemented for provider " + descriptor().providerId());
  }

  default List<ResourceDescriptor> listResources() {
    return List.of();
  }

  default ZalavaOperationResult readResource(String uri, InvocationContext context) {
    throw new UnsupportedOperationException(
        "Resource reads are not implemented for provider " + descriptor().providerId());
  }

  default List<PromptDescriptor> listPrompts() {
    return List.of();
  }

  default ZalavaOperationResult resolvePrompt(
      String promptName, Map<String, Object> arguments, InvocationContext context) {
    throw new UnsupportedOperationException(
        "Prompt resolution is not implemented for provider " + descriptor().providerId());
  }

  @Override
  default void close() {
    // Providers without owned resources do not need lifecycle work.
  }
}
