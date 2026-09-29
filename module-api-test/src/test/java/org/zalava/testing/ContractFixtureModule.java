package org.zalava.testing;

import java.util.List;
import java.util.Map;
import org.zalava.InvocationContext;
import org.zalava.ModuleDescriptor;
import org.zalava.ProviderCapabilities;
import org.zalava.ProviderDescriptor;
import org.zalava.ProviderFactory;
import org.zalava.ProviderFactoryContext;
import org.zalava.ProviderFactoryDescriptor;
import org.zalava.ZalavaModule;
import org.zalava.ZalavaOperationResult;
import org.zalava.ZalavaProvider;
import org.zalava.ZalavaToolDescriptor;
import tools.jackson.databind.JsonNode;

/** In-process module fixture used by the contract-kit tests without compiling an artifact. */
final class ContractFixtureModule implements ZalavaModule {
  static final String MODULE_ID = "fixture-contract-module";
  static final String PROVIDER_ID = "fixture-provider";
  static final String TOOL_NAME = "lookup";

  final boolean[] closed = {false};

  @Override
  public ModuleDescriptor descriptor() {
    return new ModuleDescriptor(MODULE_ID, "1.0.0", "Contract fixture", "Contract fixture module");
  }

  @Override
  public List<ProviderFactory> providerFactories() {
    return List.of(new FixtureProviderFactory(closed));
  }

  private static final class FixtureProviderFactory implements ProviderFactory {
    private final boolean[] closed;

    FixtureProviderFactory(boolean[] closed) {
      this.closed = closed;
    }

    @Override
    public ProviderFactoryDescriptor descriptor() {
      return new ProviderFactoryDescriptor(
          "fixture-factory", MODULE_ID, "tool", "Fixture Factory", "Fixture provider factory");
    }

    @Override
    public List<ZalavaProvider> createProviders(ProviderFactoryContext context) {
      return List.of(new FixtureProvider(context.configuration(), closed));
    }
  }

  private static final class FixtureProvider implements ZalavaProvider {
    private final Map<String, Object> configuration;
    private final boolean[] closed;

    FixtureProvider(Map<String, Object> configuration, boolean[] closed) {
      this.configuration = configuration;
      this.closed = closed;
    }

    @Override
    public ProviderDescriptor descriptor() {
      return new ProviderDescriptor(
          PROVIDER_ID,
          MODULE_ID,
          "tool",
          "Fixture",
          "Fixture provider",
          "1.0.0",
          ProviderCapabilities.toolsOnly(),
          List.of(),
          Map.of());
    }

    @Override
    public ProviderCapabilities capabilities() {
      return ProviderCapabilities.toolsOnly();
    }

    @Override
    public List<ZalavaToolDescriptor> listTools() {
      return List.of(new ZalavaToolDescriptor(TOOL_NAME, "Looks up fixture data", false));
    }

    @Override
    public ZalavaOperationResult callTool(
        String toolName, JsonNode arguments, InvocationContext context) {
      if (arguments.path("fail").asBoolean(false)) {
        return ZalavaOperationResult.failure(Map.of("status", "FIXTURE_FAILURE"));
      }
      return ZalavaOperationResult.success(
          Map.of("value", "fixture", "configured", configuration.getOrDefault("greeting", "none")));
    }

    @Override
    public void close() {
      closed[0] = true;
    }
  }
}
