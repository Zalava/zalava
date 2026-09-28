package org.zalava.testing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.zalava.InvocationContext;
import org.zalava.ProviderFactory;
import org.zalava.ProviderFactoryContext;
import org.zalava.SeaModule;
import org.zalava.SeaOperationResult;
import org.zalava.SeaProvider;
import org.zalava.SeaToolDescriptor;
import tools.jackson.databind.JsonNode;

/**
 * Creates a module's providers through its {@link ProviderFactory} declarations and exercises their
 * tool contract at the stable {@code module-api} boundary. Per-factory configuration and secrets
 * are scoped exactly as SEA scopes them, and created providers are closed in reverse order.
 */
public final class ProviderFixture implements AutoCloseable {
  private final Map<String, SeaProvider> providers;
  private final List<SeaProvider> created;

  private ProviderFixture(Map<String, SeaProvider> providers, List<SeaProvider> created) {
    this.providers = providers;
    this.created = created;
  }

  public static ProviderFixture create(SeaModule module, ConfigFixture configuration) {
    Objects.requireNonNull(module, "module");
    Objects.requireNonNull(configuration, "configuration");
    String moduleId = module.descriptor().moduleId();
    ProviderFactoryContext context = configuration.providerContext();
    Map<String, SeaProvider> providers = new LinkedHashMap<>();
    List<SeaProvider> created = new ArrayList<>();
    try {
      for (ProviderFactory factory : module.providerFactories()) {
        ProviderFactoryContext scoped =
            context.forFactory(moduleId, factory.descriptor().factoryId());
        for (SeaProvider provider : factory.createProviders(scoped)) {
          Objects.requireNonNull(provider, "provider");
          String providerId = provider.descriptor().providerId();
          if (providers.putIfAbsent(providerId, provider) != null) {
            throw new IllegalStateException("Module declared duplicate provider id: " + providerId);
          }
          created.add(provider);
        }
      }
    } catch (RuntimeException exception) {
      closeQuietly(created);
      throw exception;
    }
    return new ProviderFixture(providers, created);
  }

  /** All providers created across the module's factories, in declaration order. */
  public List<SeaProvider> providers() {
    return List.copyOf(created);
  }

  public Optional<SeaProvider> provider(String providerId) {
    return Optional.ofNullable(providers.get(providerId));
  }

  public SeaProvider requireProvider(String providerId) {
    SeaProvider provider = providers.get(providerId);
    if (provider == null) {
      throw new IllegalArgumentException("Module does not provide provider: " + providerId);
    }
    return provider;
  }

  public List<SeaToolDescriptor> tools(String providerId) {
    return requireProvider(providerId).listTools();
  }

  public SeaToolDescriptor requireTool(String providerId, String toolName) {
    Objects.requireNonNull(toolName, "toolName");
    return requireProvider(providerId).listTools().stream()
        .filter(tool -> tool.name().equals(toolName))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Provider " + providerId + " does not declare tool: " + toolName));
  }

  public SeaOperationResult invoke(String providerId, String toolName, JsonNode arguments) {
    return invoke(providerId, toolName, arguments, InvocationContext.system());
  }

  public SeaOperationResult invoke(
      String providerId, String toolName, JsonNode arguments, InvocationContext context) {
    SeaToolDescriptor tool = requireTool(providerId, toolName);
    return requireProvider(providerId).callTool(tool.name(), arguments, context);
  }

  @Override
  public void close() {
    closeQuietly(created);
  }

  private static void closeQuietly(List<SeaProvider> providers) {
    for (int index = providers.size() - 1; index >= 0; index--) {
      providers.get(index).close();
    }
  }
}
