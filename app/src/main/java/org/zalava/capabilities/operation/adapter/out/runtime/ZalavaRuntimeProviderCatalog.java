package org.zalava.capabilities.operation.adapter.out.runtime;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.zalava.api.InvocationContext;
import org.zalava.api.PromptDescriptor;
import org.zalava.api.ProviderCapabilities;
import org.zalava.api.ProviderDescriptor;
import org.zalava.api.ResourceDescriptor;
import org.zalava.api.ZalavaOperationResult;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.operation.application.port.out.ProviderCatalog;
import org.zalava.modules.runtime.application.port.in.RuntimeQueries;

/** Adds host-owned provenance only to providers actually instantiated by a loaded module. */
public final class ZalavaRuntimeProviderCatalog implements ProviderCatalog {
  private final RuntimeQueries runtimeQueries;
  private final Map<ZalavaProvider, String> homeAssistantGenerations = new IdentityHashMap<>();

  public ZalavaRuntimeProviderCatalog(RuntimeQueries runtimeQueries) {
    this.runtimeQueries = runtimeQueries;
  }

  @Override
  public Optional<ZalavaProvider> findProvider(String id) {
    return providers().stream()
        .filter(value -> value.descriptor().providerId().equals(id))
        .findFirst();
  }

  @Override
  public synchronized List<ZalavaProvider> providers() {
    var loadedProviders = runtimeQueries.loadedProviders();
    homeAssistantGenerations
        .keySet()
        .removeIf(
            provider ->
                loadedProviders.stream().noneMatch(loaded -> loaded.provider() == provider));
    return loadedProviders.stream()
        .map(
            loaded -> {
              ZalavaProvider provider = loaded.provider();
              return (ZalavaProvider)
                  new Hosted(
                      provider,
                      loaded.module().moduleId().equals(provider.descriptor().moduleId()),
                      generation(provider));
            })
        .toList();
  }

  private String generation(ZalavaProvider provider) {
    if (!"zalava-module-home-assistant".equals(provider.descriptor().moduleId())) return null;
    return homeAssistantGenerations.computeIfAbsent(
        provider, ignored -> UUID.randomUUID().toString());
  }

  private record Hosted(ZalavaProvider delegate, boolean owned, String generation)
      implements ZalavaProvider {
    @Override
    public ProviderDescriptor descriptor() {
      var value = delegate.descriptor();
      return new ProviderDescriptor(
          value.providerId(),
          value.moduleId(),
          value.providerType(),
          value.displayName(),
          value.description(),
          value.version(),
          value.capabilities(),
          Stream.concat(
                  value.policyTags().stream()
                      .filter(tag -> !tag.strip().equalsIgnoreCase("zalava_backed")),
                  owned ? Stream.of("zalava_backed") : Stream.empty())
              .distinct()
              .toList(),
          scope(value));
    }

    private Map<String, String> scope(ProviderDescriptor descriptor) {
      if (generation == null) return descriptor.scope();
      Map<String, String> scope = new LinkedHashMap<>(descriptor.scope());
      scope.put("zalavaProviderGeneration", generation);
      return Map.copyOf(scope);
    }

    @Override
    public ProviderCapabilities capabilities() {
      return delegate.capabilities();
    }

    @Override
    public List<ZalavaToolDescriptor> listTools() {
      return delegate.listTools();
    }

    @Override
    public ZalavaOperationResult callTool(
        String name, Map<String, Object> args, InvocationContext context) {
      return delegate.callTool(name, args, context);
    }

    @Override
    public List<ResourceDescriptor> listResources() {
      return delegate.listResources();
    }

    @Override
    public ZalavaOperationResult readResource(String uri, InvocationContext context) {
      return delegate.readResource(uri, context);
    }

    @Override
    public List<PromptDescriptor> listPrompts() {
      return delegate.listPrompts();
    }

    @Override
    public ZalavaOperationResult resolvePrompt(
        String name, Map<String, Object> args, InvocationContext context) {
      return delegate.resolvePrompt(name, args, context);
    }
  }
}
