package org.zalava.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.zalava.FactorySecretAccess;
import org.zalava.ProviderFactoryContext;
import org.zalava.ZalavaModule;
import org.zalava.ZalavaServiceContract;
import org.zalava.catalog.FileSystemModuleConfigurationStore;
import org.zalava.catalog.ModuleConfigurationSnapshot;
import org.zalava.catalog.ModuleConfigurationValidator;
import org.zalava.catalog.install.application.port.out.EnabledModuleRegistry;
import org.zalava.runtime.adapter.out.filesystem.FileSystemModuleLifecycleStore;
import org.zalava.runtime.adapter.out.filesystem.FileSystemModuleLifecycleStore.DesiredState;
import org.zalava.runtime.application.DefaultRuntimeQueries;

/** SEA-owned runtime generations separate installed descriptors from active providers. */
public final class ManagedSeaRuntime implements SeaRuntime {
  public enum State {
    SETUP_REQUIRED,
    STOPPED,
    RUNNING,
    FAILED
  }

  public record ModuleState(State state, String failure) {}

  private final List<ZalavaModule> loaded;
  private final Set<String> builtIns;
  private final FileSystemModuleLifecycleStore lifecycle;
  private final FileSystemModuleConfigurationStore configurations;
  private final ProviderFactoryContext baseContext;
  private final ModuleConfigurationValidator validator = new ModuleConfigurationValidator();
  private final Map<String, String> failures = new HashMap<>();
  private final ScheduledExecutorService retirement =
      Executors.newSingleThreadScheduledExecutor(
          Thread.ofVirtual().name("module-retirement-", 0).factory());
  private final List<DefaultRuntimeQueries> retired = new ArrayList<>();
  private volatile DefaultRuntimeQueries current;
  private volatile Set<String> activeIds = Set.of();

  public ManagedSeaRuntime(
      SeaModuleRegistry registry,
      Set<ZalavaModule> builtInModules,
      EnabledModuleRegistry enabled,
      FileSystemModuleLifecycleStore lifecycle,
      FileSystemModuleConfigurationStore configurations,
      ProviderFactoryContext baseContext) {
    this.loaded = List.copyOf(registry.modules());
    this.builtIns =
        builtInModules.stream()
            .map(module -> module.descriptor().moduleId())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    this.lifecycle = lifecycle;
    this.configurations = configurations;
    this.baseContext = baseContext;
    lifecycle.migrateExisting(
        enabled.enabledModules().stream().map(module -> module.moduleId()).toList());
    Set<String> starting = new HashSet<>(this.builtIns);
    this.current = build(starting, null, null);
    this.activeIds = Set.copyOf(starting);
    for (ZalavaModule module : loaded) {
      String id = module.descriptor().moduleId();
      if (this.builtIns.contains(id)
          || lifecycle.desired(id).orElse(DesiredState.STOPPED) != DesiredState.RUNNING) continue;
      try {
        start(id);
      } catch (RuntimeException failure) {
        failures.put(id, safeFailure(failure));
      }
    }
  }

  public synchronized ModuleState state(String moduleId) {
    ZalavaModule module = requireLoaded(moduleId);
    if (activeIds.contains(moduleId)) return new ModuleState(State.RUNNING, "");
    String failure = failures.get(moduleId);
    if (failure != null) {
      return new ModuleState(
          configurations.active(moduleId).isEmpty() ? State.SETUP_REQUIRED : State.FAILED, failure);
    }
    if (configurations.active(moduleId).isEmpty()
        && !module.configuration().jsonSchema().isEmpty()
        && !((Map<?, ?>) module.configuration().jsonSchema().getOrDefault("properties", Map.of()))
            .isEmpty()) {
      return new ModuleState(State.SETUP_REQUIRED, "");
    }
    return new ModuleState(State.STOPPED, "");
  }

  public synchronized void start(String moduleId) {
    requireExternal(moduleId);
    if (activeIds.contains(moduleId)) return;
    ZalavaModule module = requireLoaded(moduleId);
    if (configurationRequired(module) && configurations.active(moduleId).isEmpty()) {
      IllegalStateException failure =
          new IllegalStateException(
              "Module " + moduleId + " requires configuration before activation");
      failures.put(moduleId, safeFailure(failure));
      throw failure;
    }
    Set<String> next = new HashSet<>(activeIds);
    next.add(moduleId);
    try {
      DefaultRuntimeQueries candidate = build(next, null, null);
      lifecycle.set(moduleId, DesiredState.RUNNING);
      publish(candidate, next);
      failures.remove(moduleId);
    } catch (RuntimeException failure) {
      failures.put(moduleId, safeFailure(failure));
      throw failure;
    }
  }

  public synchronized void stop(String moduleId) {
    requireExternal(moduleId);
    if (!activeIds.contains(moduleId)) {
      lifecycle.set(moduleId, DesiredState.STOPPED);
      failures.remove(moduleId);
      return;
    }
    Set<String> next = new HashSet<>(activeIds);
    next.remove(moduleId);
    DefaultRuntimeQueries candidate = build(next, null, null);
    lifecycle.set(moduleId, DesiredState.STOPPED);
    publish(candidate, next);
    failures.remove(moduleId);
  }

  /** Validates a saved candidate against live factories before making it active. */
  public synchronized void applyCandidate(String moduleId) {
    requireLoaded(moduleId);
    ModuleConfigurationSnapshot candidateConfiguration =
        configurations
            .candidate(moduleId)
            .orElseThrow(
                () -> new IllegalArgumentException("No candidate configuration for " + moduleId));
    validateConfiguration(requireLoaded(moduleId), candidateConfiguration);
    DefaultRuntimeQueries candidate = null;
    try {
      if (activeIds.contains(moduleId)) {
        candidate =
            build(
                activeIds,
                candidateConfiguration,
                freezeSecrets(candidateConfiguration, configurations.candidateSecrets(moduleId)));
      }
      configurations.promoteCandidate(moduleId);
      if (candidate != null) publish(candidate, activeIds);
      failures.remove(moduleId);
    } catch (RuntimeException failure) {
      if (candidate != null) candidate.close();
      failures.put(moduleId, safeFailure(failure));
      throw failure;
    }
  }

  private DefaultRuntimeQueries build(
      Set<String> active,
      ModuleConfigurationSnapshot override,
      FactorySecretAccess overrideSecrets) {
    List<ZalavaModule> modules =
        loaded.stream().filter(module -> active.contains(module.descriptor().moduleId())).toList();
    for (ZalavaModule module : modules) {
      ModuleConfigurationSnapshot snapshot =
          override != null && override.moduleId().equals(module.descriptor().moduleId())
              ? override
              : configurations.active(module.descriptor().moduleId()).orElse(null);
      if (snapshot != null) validateConfiguration(module, snapshot);
    }
    Map<String, Object> modulesConfiguration = new HashMap<>();
    Object existing = baseContext.configuration().get("modules");
    if (existing instanceof Map<?, ?> original) {
      original.forEach((key, value) -> modulesConfiguration.put(String.valueOf(key), value));
    }
    Map<String, FactorySecretAccess> secrets = new HashMap<>(baseContext.moduleSecrets());
    configurations
        .activeConfigurations()
        .forEach(
            (id, snapshot) -> {
              modulesConfiguration.put(id, Map.of("factories", snapshot.factories()));
              secrets.put(id, freezeSecrets(snapshot, configurations.secrets(id)));
            });
    if (override != null) {
      modulesConfiguration.put(override.moduleId(), Map.of("factories", override.factories()));
      secrets.put(override.moduleId(), overrideSecrets);
    }
    ProviderFactoryContext context =
        new ProviderFactoryContext(
            Map.of("modules", Map.copyOf(modulesConfiguration)),
            baseContext.secrets(),
            Map.copyOf(secrets),
            baseContext.moduleServices());
    return new DefaultRuntimeQueries(new StaticSeaModuleRegistry(modules), context);
  }

  private void publish(DefaultRuntimeQueries candidate, Set<String> next) {
    DefaultRuntimeQueries previous = current;
    current = candidate;
    activeIds = Set.copyOf(next);
    retired.add(previous);
    retirement.schedule(
        () -> {
          synchronized (ManagedSeaRuntime.this) {
            if (retired.remove(previous)) previous.close();
          }
        },
        60,
        TimeUnit.SECONDS);
  }

  private ZalavaModule requireLoaded(String moduleId) {
    return loaded.stream()
        .filter(module -> module.descriptor().moduleId().equals(moduleId))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Module is not loaded: " + moduleId));
  }

  private static FactorySecretAccess freezeSecrets(
      ModuleConfigurationSnapshot snapshot, FactorySecretAccess source) {
    Map<String, char[]> values = new HashMap<>();
    snapshot
        .secretReferences()
        .values()
        .forEach(
            reference ->
                source.resolve(reference).ifPresent(value -> values.put(reference, value.clone())));
    return reference -> Optional.ofNullable(values.get(reference)).map(char[]::clone);
  }

  private void validateConfiguration(ZalavaModule module, ModuleConfigurationSnapshot snapshot) {
    if (!module.descriptor().moduleId().equals(snapshot.moduleId())
        || !module.descriptor().version().equals(snapshot.version())) {
      throw new IllegalArgumentException("Configuration does not match the loaded module version");
    }
    validator.validateDocument(module.configuration(), snapshot.factories());
  }

  private static boolean configurationRequired(ZalavaModule module) {
    Object properties = module.configuration().jsonSchema().get("properties");
    return properties instanceof Map<?, ?> map && !map.isEmpty();
  }

  private void requireExternal(String moduleId) {
    requireLoaded(moduleId);
    if (builtIns.contains(moduleId))
      throw new IllegalArgumentException("Bundled module cannot be stopped: " + moduleId);
  }

  private static String safeFailure(RuntimeException failure) {
    return failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage());
  }

  @Override
  public List<ZalavaModule> modules() {
    return loaded;
  }

  @Override
  public List<ZalavaModule> activeModules() {
    Set<String> active = activeIds;
    return loaded.stream()
        .filter(module -> active.contains(module.descriptor().moduleId()))
        .toList();
  }

  @Override
  public List<LoadedSeaProvider> loadedProviders() {
    return current.loadedProviders();
  }

  @Override
  public <T>
      Optional<org.zalava.runtime.application.port.in.RuntimeQueries.LoadedSeaService<T>>
          findService(ZalavaServiceContract<T> contract) {
    return current.findService(contract);
  }

  @Override
  public synchronized void close() {
    retirement.shutdownNow();
    current.close();
    retired.forEach(DefaultRuntimeQueries::close);
    retired.clear();
  }
}
