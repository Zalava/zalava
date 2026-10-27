package org.zalava.capabilities.operation.adapter.out.runtime;

import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  public List<ZalavaProvider> providers() {
    return runtimeQueries.loadedProviders().stream()
        .map(
            loaded -> {
              ZalavaProvider provider = loaded.provider();
              return (ZalavaProvider)
                  new Hosted(
                      provider,
                      loaded.module().moduleId().equals(provider.descriptor().moduleId()));
            })
        .toList();
  }

  private record Hosted(ZalavaProvider delegate, boolean owned) implements ZalavaProvider {
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
          value.scope());
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
