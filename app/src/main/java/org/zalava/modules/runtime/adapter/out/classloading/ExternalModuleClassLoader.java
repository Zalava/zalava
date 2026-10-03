package org.zalava.modules.runtime.adapter.out.classloading;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.jar.JarFile;
import org.zalava.api.ModuleDescriptor;
import org.zalava.api.ProviderDescriptor;
import org.zalava.api.ProviderFactory;
import org.zalava.api.ProviderFactoryContext;
import org.zalava.api.ProviderFactoryDescriptor;
import org.zalava.api.ZalavaModule;
import org.zalava.api.ZalavaProvider;
import org.zalava.modules.catalog.install.application.port.out.EnabledModuleRegistry;
import org.zalava.modules.catalog.install.application.port.out.ModuleEnablement;
import org.zalava.modules.runtime.ExternalZalavaModuleLoadingException;
import org.zalava.modules.runtime.application.port.in.ExternalModuleLoading;
import org.zalava.modules.runtime.domain.RuntimeCompatibility;
import org.zalava.modules.runtime.domain.RuntimeVersion;

/** JDK classloading adapter for the enabled external-module registry. */
public final class ExternalModuleClassLoader implements ExternalModuleLoading {

  private static final RuntimeVersion CURRENT_RUNTIME = RuntimeVersion.parse("1.0.0");
  private static final String RETIRED_PREVIEW_SERVICE_DESCRIPTOR =
      "META-INF/services/org.zalava.zalava.ZalavaModule";
  private static final Set<String> FORBIDDEN_HOST_PACKAGES =
      Set.of(
          "api",
          "identity",
          "assistant",
          "capabilities",
          "tasks",
          "knowledge",
          "platform",
          "web",
          "modules/catalog",
          "modules/runtime",
          "modules/managedservices",
          "modules/development",
          "modules/web");

  private final EnabledModuleRegistry enabledModuleRegistry;
  private final ProviderFactoryContext providerFactoryContext;
  private final List<URLClassLoader> classLoaders = new ArrayList<>();
  private boolean loaded;

  public ExternalModuleClassLoader(EnabledModuleRegistry enabledModuleRegistry) {
    this(enabledModuleRegistry, ProviderFactoryContext.empty());
  }

  public ExternalModuleClassLoader(
      EnabledModuleRegistry enabledModuleRegistry, ProviderFactoryContext providerFactoryContext) {
    this.enabledModuleRegistry = enabledModuleRegistry;
    this.providerFactoryContext =
        providerFactoryContext == null ? ProviderFactoryContext.empty() : providerFactoryContext;
  }

  public synchronized List<ZalavaModule> loadModules() {
    if (loaded) {
      throw new IllegalStateException("External Zalava modules have already been loaded");
    }
    loaded = true;
    List<ModuleEnablement.EnabledModule> enabledModules = enabledModuleRegistry.enabledModules();
    List<ZalavaModule> loadedModules = new ArrayList<>();
    Map<String, String> packageOwners = new HashMap<>();
    for (ModuleEnablement.EnabledModule enabled : enabledModules) {
      try {
        loadedModules.addAll(loadModule(enabled, packageOwners));
      } catch (ExternalZalavaModuleLoadingException | ServiceConfigurationError exception) {
        System.getLogger(ExternalModuleClassLoader.class.getName())
            .log(
                System.Logger.Level.WARNING,
                "External module "
                    + enabled.moduleId()
                    + " was not loaded; failure types: "
                    + failureTypes(exception));
      }
    }
    return List.copyOf(loadedModules);
  }

  private List<ZalavaModule> loadModule(
      ModuleEnablement.EnabledModule enabled, Map<String, String> packageOwners) {
    List<Path> artifacts = artifactPaths(enabled);
    Set<String> claimedPackages = validatePackageOwnership(enabled, artifacts, packageOwners);
    URLClassLoader classLoader =
        new URLClassLoader(artifactUrls(artifacts), ZalavaModule.class.getClassLoader());
    classLoaders.add(classLoader);
    try {
      if (classLoader.getResource(RETIRED_PREVIEW_SERVICE_DESCRIPTOR) != null) {
        throw loadingFailure(previewServiceDescriptorError(enabled.moduleId()));
      }
      List<ZalavaModule> modules =
          ServiceLoader.load(ZalavaModule.class, classLoader).stream()
              .map(ServiceLoader.Provider::get)
              .toList();
      validateLoadedModules(List.of(enabled), modules, false);
      return modules;
    } catch (ServiceConfigurationError | RuntimeException exception) {
      close(classLoader);
      classLoaders.remove(classLoader);
      claimedPackages.forEach(packageName -> packageOwners.remove(packageName, enabled.moduleId()));
      if (exception instanceof ExternalZalavaModuleLoadingException loadingException) {
        throw loadingException;
      }
      throw new ExternalZalavaModuleLoadingException(
          "Unable to load external Zalava module services", exception);
    }
  }

  @Override
  public synchronized void close() throws IOException {
    IOException failure = null;
    for (int index = classLoaders.size() - 1; index >= 0; index--) {
      try {
        classLoaders.get(index).close();
      } catch (IOException exception) {
        if (failure == null) {
          failure = exception;
        } else {
          failure.addSuppressed(exception);
        }
      }
    }
    classLoaders.clear();
    if (failure != null) throw failure;
  }

  private static URL[] artifactUrls(List<Path> artifacts) {
    return artifacts.stream().map(ExternalModuleClassLoader::artifactUrl).toArray(URL[]::new);
  }

  private static URL artifactUrl(Path artifact) {
    try {
      return artifact.toUri().toURL();
    } catch (IOException ex) {
      throw new ExternalZalavaModuleLoadingException("Invalid external module artifact path", ex);
    }
  }

  private static List<Path> artifactPaths(ModuleEnablement.EnabledModule module) {
    List<Path> artifacts = new ArrayList<>();
    artifacts.add(requireRegularArtifact(module, module.artifactPath(), "primary"));
    for (ModuleEnablement.RuntimeArtifact runtimeArtifact : module.runtimeArtifacts()) {
      artifacts.add(requireRegularArtifact(module, runtimeArtifact.artifactPath(), "runtime"));
    }
    if (new HashSet<>(artifacts).size() != artifacts.size()) {
      throw loadingFailure(module.moduleId(), "artifact bundle paths must be unique");
    }
    return List.copyOf(artifacts);
  }

  private static Path requireRegularArtifact(
      ModuleEnablement.EnabledModule module, String artifactPath, String artifactType) {
    if (artifactPath == null || artifactPath.isBlank()) {
      throw loadingFailure(module.moduleId(), artifactType + " artifact path is required");
    }
    Path artifact = Path.of(artifactPath).toAbsolutePath().normalize();
    if (!Files.isRegularFile(artifact)) {
      throw loadingFailure(module.moduleId(), artifactType + " artifact must be a regular file");
    }
    return artifact;
  }

  private static Set<String> validatePackageOwnership(
      ModuleEnablement.EnabledModule module,
      List<Path> artifacts,
      Map<String, String> packageOwners) {
    Set<String> packages = new HashSet<>();
    for (Path artifact : artifacts) {
      packages.addAll(packagesIn(module, artifact));
    }
    for (String packageName : packages) {
      if (isForbiddenHostPackage(packageName)) {
        throw loadingFailure(
            module.moduleId(),
            "artifact bundle contains forbidden host/API package " + packageName);
      }
      if (!requiresExclusiveOwnership(packageName)) {
        continue;
      }
      String owner = packageOwners.get(packageName);
      if (owner != null && !owner.equals(module.moduleId())) {
        throw loadingFailure(
            module.moduleId(),
            "artifact bundle package " + packageName + " is already owned by " + owner);
      }
    }
    packages.stream()
        .filter(ExternalModuleClassLoader::requiresExclusiveOwnership)
        .forEach(packageName -> packageOwners.put(packageName, module.moduleId()));
    return packages;
  }

  private static Set<String> packagesIn(ModuleEnablement.EnabledModule module, Path artifact) {
    try (JarFile jarFile = new JarFile(artifact.toFile())) {
      Set<String> packages = new HashSet<>();
      jarFile.stream()
          .filter(entry -> !entry.isDirectory() && entry.getName().endsWith(".class"))
          .map(entry -> packageName(entry.getName()))
          .filter(packageName -> packageName != null)
          .forEach(packages::add);
      return packages;
    } catch (IOException exception) {
      throw new ExternalZalavaModuleLoadingException(
          "Unable inspect external module artifact bundle for " + module.moduleId(), exception);
    }
  }

  private static String packageName(String classEntry) {
    int separator = classEntry.lastIndexOf('/');
    return separator < 0 ? null : classEntry.substring(0, separator);
  }

  private static boolean isForbiddenHostPackage(String packageName) {
    if (packageName.equals("org/zalava")) {
      return true;
    }
    String prefix = "org/zalava/";
    if (!packageName.startsWith(prefix)) {
      return false;
    }
    String relative = packageName.substring(prefix.length());
    return FORBIDDEN_HOST_PACKAGES.stream()
        .anyMatch(name -> relative.equals(name) || relative.startsWith(name + "/"));
  }

  static boolean requiresExclusiveOwnership(String packageName) {
    return packageName.equals("org/zalava") || packageName.startsWith("org/zalava/");
  }

  private static ExternalZalavaModuleLoadingException loadingFailure(
      String moduleId, String message) {
    return new ExternalZalavaModuleLoadingException("External module " + moduleId + " " + message);
  }

  public void validateLoadedModules(
      List<ModuleEnablement.EnabledModule> enabledModules, List<ZalavaModule> loaded) {
    validateLoadedModules(enabledModules, loaded, true);
  }

  private void validateLoadedModules(
      List<ModuleEnablement.EnabledModule> enabledModules,
      List<ZalavaModule> loaded,
      boolean createProviders) {
    List<String> expected =
        enabledModules.stream().map(ModuleEnablement.EnabledModule::moduleId).sorted().toList();
    List<String> actual = new ArrayList<>();
    for (ZalavaModule module : loaded) {
      ModuleDescriptor moduleDescriptor = requireModuleDescriptor(module);
      ModuleEnablement.EnabledModule enabled =
          enabledModules.stream()
              .filter(candidate -> candidate.moduleId().equals(moduleDescriptor.moduleId()))
              .findFirst()
              .orElseThrow(
                  () ->
                      loadingFailure(
                          "External artifact declared unregistered module "
                              + moduleDescriptor.moduleId()));
      validateCompatibility(enabled);
      if (!enabled.version().equals(moduleDescriptor.version())) {
        throw loadingFailure(
            "External module "
                + enabled.moduleId()
                + " reported version "
                + moduleDescriptor.version()
                + " but enabled version is "
                + enabled.version());
      }
      validateProviderContracts(module, moduleDescriptor, createProviders);
      actual.add(moduleDescriptor.moduleId());
    }
    actual.sort(String::compareTo);
    if (!actual.equals(expected)) {
      throw loadingFailure(
          "Enabled external modules " + expected + " did not match discovered services " + actual);
    }
  }

  private ModuleDescriptor requireModuleDescriptor(ZalavaModule module) {
    if (module == null) {
      throw loadingFailure("External Zalava module service must not be null");
    }
    ModuleDescriptor descriptor = module.descriptor();
    if (descriptor == null) {
      throw loadingFailure("External Zalava module descriptor must not be null");
    }
    requireText(descriptor.moduleId(), "External Zalava module id");
    requireText(
        descriptor.version(), "External Zalava module " + descriptor.moduleId() + " version");
    requireText(
        descriptor.displayName(),
        "External Zalava module " + descriptor.moduleId() + " display name");
    requireText(
        descriptor.description(),
        "External Zalava module " + descriptor.moduleId() + " description");
    return descriptor;
  }

  private void validateProviderContracts(
      ZalavaModule module, ModuleDescriptor moduleDescriptor, boolean createProviders) {
    List<ProviderFactory> factories = module.providerFactories();
    if (factories == null) {
      throw loadingFailure(
          "External module "
              + moduleDescriptor.moduleId()
              + " provider factories must not be null");
    }
    for (ProviderFactory factory : factories) {
      if (factory == null) {
        throw loadingFailure(
            "External module "
                + moduleDescriptor.moduleId()
                + " provider factory must not be null");
      }
      ProviderFactoryDescriptor factoryDescriptor = factory.descriptor();
      if (factoryDescriptor == null) {
        throw loadingFailure(
            "External module "
                + moduleDescriptor.moduleId()
                + " provider factory descriptor must not be null");
      }
      requireText(factoryDescriptor.factoryId(), "External provider factory id");
      requireMatchingModule(
          moduleDescriptor,
          factoryDescriptor.moduleId(),
          "provider factory " + factoryDescriptor.factoryId());
      requireText(
          factoryDescriptor.providerType(),
          "External provider factory " + factoryDescriptor.factoryId() + " provider type");
      requireText(
          factoryDescriptor.displayName(),
          "External provider factory " + factoryDescriptor.factoryId() + " display name");
      requireText(
          factoryDescriptor.description(),
          "External provider factory " + factoryDescriptor.factoryId() + " description");
      // Discovery must leave stopped modules configurable without allocating providers.
      // Runtime activation validates and owns their configured provider instances.
      if (createProviders) validateProviders(moduleDescriptor, factory, factoryDescriptor);
    }
  }

  private void validateProviders(
      ModuleDescriptor moduleDescriptor,
      ProviderFactory factory,
      ProviderFactoryDescriptor factoryDescriptor) {
    List<ZalavaProvider> providers;
    try {
      providers =
          factory.createProviders(
              providerFactoryContext.forFactory(
                  moduleDescriptor.moduleId(), factoryDescriptor.factoryId()));
    } catch (RuntimeException ex) {
      throw loadingFailure(
          "External provider factory "
              + factoryDescriptor.factoryId()
              + " failed to create providers: "
              + ex.getMessage());
    }
    if (providers == null) {
      throw loadingFailure(
          "External provider factory "
              + factoryDescriptor.factoryId()
              + " providers must not be null");
    }
    try {
      for (ZalavaProvider provider : providers) {
        if (provider == null) {
          throw loadingFailure(
              "External provider factory "
                  + factoryDescriptor.factoryId()
                  + " provider must not be null");
        }
        ProviderDescriptor providerDescriptor = provider.descriptor();
        if (providerDescriptor == null) {
          throw loadingFailure(
              "External provider from factory "
                  + factoryDescriptor.factoryId()
                  + " descriptor must not be null");
        }
        requireText(providerDescriptor.providerId(), "External provider id");
        requireMatchingModule(
            moduleDescriptor,
            providerDescriptor.moduleId(),
            "provider " + providerDescriptor.providerId());
        requireText(
            providerDescriptor.providerType(),
            "External provider " + providerDescriptor.providerId() + " provider type");
        requireText(
            providerDescriptor.displayName(),
            "External provider " + providerDescriptor.providerId() + " display name");
        requireText(
            providerDescriptor.description(),
            "External provider " + providerDescriptor.providerId() + " description");
        requireText(
            providerDescriptor.version(),
            "External provider " + providerDescriptor.providerId() + " version");
        if (providerDescriptor.capabilities() == null) {
          throw loadingFailure(
              "External provider "
                  + providerDescriptor.providerId()
                  + " capabilities must not be null");
        }
      }
    } finally {
      closeValidatedProviders(providers);
    }
  }

  private void closeValidatedProviders(List<ZalavaProvider> providers) {
    for (int index = providers.size() - 1; index >= 0; index--) {
      ZalavaProvider provider = providers.get(index);
      if (provider == null) {
        continue;
      }
      try {
        provider.close();
      } catch (Exception exception) {
        throw loadingFailure(
            "Unable to close validated external provider: " + exception.getMessage());
      }
    }
  }

  private void requireMatchingModule(
      ModuleDescriptor moduleDescriptor, String moduleId, String subject) {
    requireText(moduleId, "External " + subject + " module id");
    if (!moduleDescriptor.moduleId().equals(moduleId)) {
      throw loadingFailure(
          "External "
              + subject
              + " belongs to module "
              + moduleId
              + " but owning module is "
              + moduleDescriptor.moduleId());
    }
  }

  private void requireText(String value, String field) {
    if (value == null || value.isBlank()) {
      throw loadingFailure(field + " must not be blank");
    }
  }

  private void validateCompatibility(ModuleEnablement.EnabledModule enabled) {
    String compatibility = enabled.zalavaRuntimeCompatibility();
    if (compatibility == null || compatibility.isBlank()) {
      return;
    }
    RuntimeCompatibility range;
    try {
      range = RuntimeCompatibility.parse(compatibility);
    } catch (IllegalArgumentException ex) {
      throw loadingFailure(
          "External module "
              + enabled.moduleId()
              + " zalavaRuntime compatibility is invalid: "
              + ex.getMessage());
    }
    if (range.minimum().compareTo(CURRENT_RUNTIME) > 0) {
      throw loadingFailure(
          "External module "
              + enabled.moduleId()
              + " requires Zalava runtime "
              + compatibility
              + " but current runtime is "
              + CURRENT_RUNTIME.value());
    }
    if (!range.supports(CURRENT_RUNTIME)) {
      throw loadingFailure(
          "External module "
              + enabled.moduleId()
              + " does not support Zalava runtime "
              + CURRENT_RUNTIME.value()
              + " (requires "
              + compatibility
              + ")");
    }
  }

  private static void close(URLClassLoader classLoader) {
    try {
      classLoader.close();
    } catch (IOException ignored) {
      // Preserve the primary loading failure.
    }
  }

  static String failureTypes(Throwable failure) {
    List<String> types = new ArrayList<>();
    for (Throwable current = failure; current != null; current = current.getCause()) {
      types.add(current.getClass().getName());
    }
    return String.join(" -> ", types);
  }

  static String previewServiceDescriptorError(String moduleId) {
    return "External module "
        + moduleId
        + " uses retired preview SPI service descriptor "
        + RETIRED_PREVIEW_SERVICE_DESCRIPTOR
        + "; rebuild it against org.zalava:module-api:1.0.0 "
        + "and register META-INF/services/org.zalava.api.ZalavaModule";
  }

  private ExternalZalavaModuleLoadingException loadingFailure(String message) {
    return new ExternalZalavaModuleLoadingException(message);
  }
}
