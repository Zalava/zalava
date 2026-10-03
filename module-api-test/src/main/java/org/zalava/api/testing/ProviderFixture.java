package org.zalava.api.testing;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.zalava.api.InvocationContext;
import org.zalava.api.ProviderFactory;
import org.zalava.api.ProviderFactoryContext;
import org.zalava.api.ZalavaModule;
import org.zalava.api.ZalavaOperationResult;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;

/**
 * Creates a module's providers through its {@link ProviderFactory} declarations and exercises their
 * tool contract at the stable {@code module-api} boundary. Per-factory configuration and secrets
 * are scoped exactly as Zalava scopes them, and created providers are closed in reverse order.
 */
public final class ProviderFixture implements AutoCloseable {
  private final Map<String, ZalavaProvider> providers;
  private final List<ZalavaProvider> created;

  private ProviderFixture(Map<String, ZalavaProvider> providers, List<ZalavaProvider> created) {
    this.providers = providers;
    this.created = created;
  }

  public static ProviderFixture create(ZalavaModule module, ConfigFixture configuration) {
    Objects.requireNonNull(module, "module");
    Objects.requireNonNull(configuration, "configuration");
    String moduleId = module.descriptor().moduleId();
    ProviderFactoryContext context = configuration.providerContext();
    Map<String, ZalavaProvider> providers = new LinkedHashMap<>();
    List<ZalavaProvider> created = new ArrayList<>();
    try {
      for (ProviderFactory factory : module.providerFactories()) {
        ProviderFactoryContext scoped =
            context.forFactory(moduleId, factory.descriptor().factoryId());
        for (ZalavaProvider provider : factory.createProviders(scoped)) {
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
  public List<ZalavaProvider> providers() {
    return List.copyOf(created);
  }

  public Optional<ZalavaProvider> provider(String providerId) {
    return Optional.ofNullable(providers.get(providerId));
  }

  public ZalavaProvider requireProvider(String providerId) {
    ZalavaProvider provider = providers.get(providerId);
    if (provider == null) {
      throw new IllegalArgumentException("Module does not provide provider: " + providerId);
    }
    return provider;
  }

  public List<ZalavaToolDescriptor> tools(String providerId) {
    return requireProvider(providerId).listTools();
  }

  public ZalavaToolDescriptor requireTool(String providerId, String toolName) {
    Objects.requireNonNull(toolName, "toolName");
    return requireProvider(providerId).listTools().stream()
        .filter(tool -> tool.name().equals(toolName))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Provider " + providerId + " does not declare tool: " + toolName));
  }

  public ZalavaOperationResult invoke(
      String providerId, String toolName, Map<String, Object> arguments) {
    return invoke(providerId, toolName, arguments, InvocationContext.system());
  }

  public ZalavaOperationResult invoke(
      String providerId,
      String toolName,
      Map<String, Object> arguments,
      InvocationContext context) {
    ZalavaToolDescriptor tool = requireTool(providerId, toolName);
    return requireProvider(providerId)
        .callTool(tool.name(), org.zalava.api.JsonArguments.immutable(arguments), context);
  }

  @Override
  public void close() {
    closeQuietly(created);
  }

  private static void closeQuietly(List<ZalavaProvider> providers) {
    for (int index = providers.size() - 1; index >= 0; index--) {
      providers.get(index).close();
    }
  }
}
