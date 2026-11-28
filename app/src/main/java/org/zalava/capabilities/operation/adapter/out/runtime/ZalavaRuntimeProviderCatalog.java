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
  private final Map<ZalavaProvider, UUID> providerGenerations = new IdentityHashMap<>();

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
    providerGenerations
        .keySet()
        .removeIf(
            provider ->
                loadedProviders.stream().noneMatch(loaded -> loaded.provider() == provider));
    return loadedProviders.stream()
        .map(
            loaded -> {
              ZalavaProvider provider = loaded.provider();
              boolean owned = loaded.module().moduleId().equals(provider.descriptor().moduleId());
              return (ZalavaProvider)
                  new Hosted(provider, owned, owned ? generation(provider) : null);
            })
        .toList();
  }

  private UUID generation(ZalavaProvider provider) {
    return providerGenerations.computeIfAbsent(provider, ignored -> UUID.randomUUID());
  }

  @Override
  public synchronized Optional<UUID> generationOf(ZalavaProvider provider) {
    if (!(provider instanceof Hosted hosted)
        || !hosted.owned()
        || !hosted.generation().equals(providerGenerations.get(hosted.delegate()))) {
      return Optional.empty();
    }
    boolean stillLoaded =
        runtimeQueries.loadedProviders().stream()
            .anyMatch(
                loaded ->
                    loaded.provider() == hosted.delegate()
                        && loaded
                            .module()
                            .moduleId()
                            .equals(hosted.delegate().descriptor().moduleId()));
    if (!stillLoaded) {
      providerGenerations.remove(hosted.delegate());
      return Optional.empty();
    }
    return Optional.of(hosted.generation());
  }

  private record Hosted(ZalavaProvider delegate, boolean owned, UUID generation)
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
      // Compatibility for approvals created under the previous scope-based scheme.
      if (generation == null || !"zalava-module-home-assistant".equals(descriptor.moduleId()))
        return descriptor.scope();
      Map<String, String> scope = new LinkedHashMap<>(descriptor.scope());
      scope.put("zalavaProviderGeneration", generation.toString());
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
