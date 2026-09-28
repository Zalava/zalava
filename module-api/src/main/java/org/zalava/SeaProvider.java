package org.zalava;

import java.util.List;
import tools.jackson.databind.JsonNode;

public interface SeaProvider extends AutoCloseable {

  ProviderDescriptor descriptor();

  ProviderCapabilities capabilities();

  List<SeaToolDescriptor> listTools();

  default SeaOperationResult callTool(
      String toolName, JsonNode arguments, InvocationContext context) {
    throw new UnsupportedOperationException(
        "Tool execution is not implemented for provider " + descriptor().providerId());
  }

  default List<ResourceDescriptor> listResources() {
    return List.of();
  }

  default SeaOperationResult readResource(String uri, InvocationContext context) {
    throw new UnsupportedOperationException(
        "Resource reads are not implemented for provider " + descriptor().providerId());
  }

  default List<PromptDescriptor> listPrompts() {
    return List.of();
  }

  default SeaOperationResult resolvePrompt(
      String promptName, JsonNode arguments, InvocationContext context) {
    throw new UnsupportedOperationException(
        "Prompt resolution is not implemented for provider " + descriptor().providerId());
  }

  @Override
  default void close() {
    // Providers without owned resources do not need lifecycle work.
  }
}
