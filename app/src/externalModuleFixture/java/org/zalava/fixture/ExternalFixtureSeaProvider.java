package org.zalava.fixture;

import java.util.List;
import java.util.Map;
import org.zalava.InvocationContext;
import org.zalava.ProviderCapabilities;
import org.zalava.ProviderDescriptor;
import org.zalava.SeaOperationResult;
import org.zalava.SeaProvider;
import org.zalava.SeaToolDescriptor;
import tools.jackson.databind.JsonNode;

final class ExternalFixtureSeaProvider implements SeaProvider {

  @Override
  public ProviderDescriptor descriptor() {
    return new ProviderDescriptor(
        "external-fixture-provider",
        "sea-external-module-fixture",
        "external-fixture",
        "External fixture provider",
        "Provider loaded from a separately packaged JAR",
        "1.0.0",
        ProviderCapabilities.toolsOnly(),
        List.of("external"),
        Map.of());
  }

  @Override
  public ProviderCapabilities capabilities() {
    return descriptor().capabilities();
  }

  @Override
  public List<SeaToolDescriptor> listTools() {
    return List.of(
        new SeaToolDescriptor(
            "example_lookup",
            "Returns the fixture value",
            false,
            List.of("external"),
            Map.of("type", "object")));
  }

  @Override
  public SeaOperationResult callTool(
      String toolName, JsonNode arguments, InvocationContext context) {
    if ("example_lookup".equals(toolName) && arguments.path("fail").asBoolean()) {
      return SeaOperationResult.failure(Map.of("code", "FIXTURE_FAILURE"));
    }
    if ("example_lookup".equals(toolName))
      return SeaOperationResult.success(Map.of("value", "fixture"));
    return SeaOperationResult.failure(Map.of("code", "UNKNOWN_TOOL"));
  }
}
