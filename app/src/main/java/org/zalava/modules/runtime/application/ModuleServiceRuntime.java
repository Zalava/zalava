package org.zalava.modules.runtime.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.zalava.ProviderFactoryContext;
import org.zalava.RequirementMode;
import org.zalava.ZalavaModule;
import org.zalava.ZalavaServiceContract;
import org.zalava.ZalavaServiceFactory;
import org.zalava.ZalavaServiceFactoryContext;
import org.zalava.ZalavaServiceRequirement;
import org.zalava.modules.runtime.application.port.in.RuntimeQueries;

/** Fail-closed resolver for SEA-owned typed services, independent of Spring. */
final class ModuleServiceRuntime implements AutoCloseable {
  private final Map<String, ZalavaModule> modules = new HashMap<>();
  private final Map<String, ZalavaServiceFactory<?>> factories = new HashMap<>();
  private final Map<String, Object> instances = new HashMap<>();
  private final List<AutoCloseable> closeables = new ArrayList<>();
  private final ProviderFactoryContext source;

  ModuleServiceRuntime(List<ZalavaModule> modules, ProviderFactoryContext source) {
    this.source = source == null ? ProviderFactoryContext.empty() : source;
    for (ZalavaModule module : modules) {
      this.modules.put(module.descriptor().moduleId(), module);
      for (ZalavaServiceFactory<?> factory : module.serviceFactories()) register(module, factory);
    }
    validateRequirements();
    try {
      for (String moduleId : this.modules.keySet())
        resolve(moduleId, new HashSet<>(), new HashSet<>());
    } catch (RuntimeException exception) {
      closeQuietly();
      throw exception;
    }
  }

  ProviderFactoryContext providerContext(ProviderFactoryContext source) {
    Map<String, ZalavaServiceFactoryContext> scopes = new HashMap<>();
    for (ZalavaModule module : modules.values())
      scopes.put(module.descriptor().moduleId(), scope(module));
    return source.withTypedServices(scopes);
  }

  <T> java.util.Optional<RuntimeQueries.LoadedSeaService<T>> findService(
      ZalavaServiceContract<T> contract) {
    Objects.requireNonNull(contract, "contract");
    ZalavaServiceFactory<?> factory = factories.get(contract.serviceId());
    Object instance = instances.get(contract.serviceId());
    if (factory == null
        || instance == null
        || !factory.contract().contractVersion().equals(contract.contractVersion())
        || !contract.serviceType().isInstance(instance)) {
      return java.util.Optional.empty();
    }
    return java.util.Optional.of(
        new RuntimeQueries.LoadedSeaService<>(
            factory.descriptor(), contract.serviceType().cast(instance)));
  }

  private void register(ZalavaModule module, ZalavaServiceFactory<?> factory) {
    if (factory == null || factory.descriptor() == null || factory.contract() == null) {
      throw new IllegalStateException(
          "SEA service factories must declare a descriptor and contract");
    }
    String id = factory.descriptor().serviceId();
    if (!module.descriptor().moduleId().equals(factory.descriptor().moduleId())) {
      throw new IllegalStateException("SEA service " + id + " is declared by the wrong module");
    }
    if (!id.equals(factory.contract().serviceId())
        || !factory.descriptor().contractVersion().equals(factory.contract().contractVersion())) {
      throw new IllegalStateException("SEA service descriptor does not match contract: " + id);
    }
    if (factories.putIfAbsent(id, factory) != null) {
      throw new IllegalStateException("Multiple SEA service providers declared for " + id);
    }
  }

  private void validateRequirements() {
    for (ZalavaModule module : modules.values()) {
      for (ZalavaServiceRequirement requirement : requirements(module).values()) {
        ZalavaServiceFactory<?> factory = factories.get(requirement.serviceId());
        if (requirement.mode() == RequirementMode.REQUIRED
            && (factory == null || !matches(requirement, factory))) {
          throw new IllegalStateException(
              "Module "
                  + module.descriptor().moduleId()
                  + " requires unavailable SEA service "
                  + requirement.serviceId());
        }
      }
    }
  }

  private void resolve(String moduleId, Set<String> visiting, Set<String> complete) {
    if (complete.contains(moduleId)) return;
    if (!visiting.add(moduleId))
      throw new IllegalStateException("Cyclic SEA service dependency involving module " + moduleId);
    ZalavaModule module = modules.get(moduleId);
    for (ZalavaServiceRequirement requirement : requirements(module).values()) {
      ZalavaServiceFactory<?> factory = factories.get(requirement.serviceId());
      if (factory != null && matches(requirement, factory))
        resolve(factory.descriptor().moduleId(), visiting, complete);
    }
    for (ZalavaServiceFactory<?> factory : module.serviceFactories()) create(module, factory);
    visiting.remove(moduleId);
    complete.add(moduleId);
  }

  private void create(ZalavaModule module, ZalavaServiceFactory<?> factory) {
    String id = factory.descriptor().serviceId();
    if (instances.containsKey(id)) return;
    Object value = factory.create(scope(module));
    if (!factory.contract().serviceType().isInstance(value)) {
      throw new IllegalStateException(
          "SEA service factory returned an incompatible implementation for " + id);
    }
    closeables.add(factory);
    instances.put(id, value);
    if (value instanceof AutoCloseable closeable) closeables.add(closeable);
  }

  private ZalavaServiceFactoryContext scope(ZalavaModule module) {
    Map<String, ZalavaServiceRequirement> requirements = requirements(module);
    Map<ZalavaServiceContract<?>, Object> available = new HashMap<>();
    for (ZalavaServiceRequirement requirement : requirements.values()) {
      ZalavaServiceFactory<?> factory = factories.get(requirement.serviceId());
      if (factory != null
          && matches(requirement, factory)
          && instances.containsKey(requirement.serviceId())) {
        available.put(factory.contract(), instances.get(requirement.serviceId()));
      }
    }
    ProviderFactoryContext scoped = source.forFactory(module.descriptor().moduleId(), "services");
    return new ZalavaServiceFactoryContext(
        module.descriptor().moduleId(),
        available,
        requirements,
        scoped.configuration(),
        scoped.secrets());
  }

  private static Map<String, ZalavaServiceRequirement> requirements(ZalavaModule module) {
    Map<String, ZalavaServiceRequirement> result = new HashMap<>();
    for (ZalavaServiceRequirement requirement : module.serviceRequirements()) {
      if (result.putIfAbsent(requirement.serviceId(), requirement) != null) {
        throw new IllegalStateException(
            "Module "
                + module.descriptor().moduleId()
                + " declares duplicate SEA service "
                + requirement.serviceId());
      }
    }
    return result;
  }

  private static boolean matches(
      ZalavaServiceRequirement requirement, ZalavaServiceFactory<?> factory) {
    return "*".equals(requirement.versionRange())
        || requirement.versionRange().equals(factory.contract().contractVersion());
  }

  @Override
  public void close() {
    RuntimeException failure = closeQuietly();
    if (failure != null) throw failure;
  }

  private RuntimeException closeQuietly() {
    RuntimeException failure = null;
    for (int i = closeables.size() - 1; i >= 0; i--) {
      try {
        closeables.get(i).close();
      } catch (Exception exception) {
        if (failure == null) failure = new IllegalStateException("Unable to close SEA services");
        failure.addSuppressed(exception);
      }
    }
    closeables.clear();
    return failure;
  }
}
