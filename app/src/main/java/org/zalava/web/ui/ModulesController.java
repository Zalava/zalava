package org.zalava.web.ui;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.zalava.api.ModuleDescriptor;
import org.zalava.api.ProviderDescriptor;
import org.zalava.api.ZalavaModule;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.api.extensions.managed.ManagedServiceDeclaration;
import org.zalava.modules.catalog.FileSystemModuleConfigurationStore;
import org.zalava.modules.catalog.ModuleConfigurationSnapshot;
import org.zalava.modules.catalog.ModuleConfigurationValidator;
import org.zalava.modules.catalog.ModuleReleaseInstallRequest;
import org.zalava.modules.catalog.ModuleReleaseVersion;
import org.zalava.modules.catalog.install.application.port.in.EnabledModuleManagement;
import org.zalava.modules.catalog.install.application.port.in.LocalDevelopmentProjectInstallation;
import org.zalava.modules.catalog.install.application.port.in.ModuleLocatorInstallation;
import org.zalava.modules.catalog.install.application.port.in.ModuleQueries;
import org.zalava.modules.catalog.install.application.port.in.ModuleReleaseInstallation;
import org.zalava.modules.catalog.install.application.port.in.UploadedModuleInstallation;
import org.zalava.modules.managedservices.application.ManagedServiceInstallException;
import org.zalava.modules.managedservices.application.port.in.DeclaredManagedServices;
import org.zalava.modules.managedservices.application.port.in.ManagedServiceQueries;
import org.zalava.modules.managedservices.application.port.in.ModuleManagedServiceInstallation;
import org.zalava.modules.runtime.LoadedZalavaProvider;
import org.zalava.modules.runtime.ManagedZalavaRuntime;
import org.zalava.modules.runtime.ZalavaRuntime;
import org.zalava.modules.runtime.application.port.in.ManagedZalavaRestart;

@Controller
public class ModulesController {

  private static final ModuleConfigurationValidator CONFIGURATION_VALIDATOR =
      new ModuleConfigurationValidator();

  private static final int RECENT_INSTALLATION_LIMIT = 20;
  private static final int MAX_MODULE_ID_LENGTH = 160;
  private static final int MAX_VERSION_LENGTH = 2_000;
  private static final int MAX_REQUEST_ID_LENGTH = 128;
  private static final int MAX_ERROR_LENGTH = 300;

  private final ZalavaRuntime zalavaRuntime;
  private final ModuleQueries moduleQueries;
  private final FileSystemModuleConfigurationStore moduleConfigurationStore;
  private final ModuleMarketplace marketplace;
  private final LocalDevelopmentProjectInstallation localDevelopmentProjects;
  private final ModuleLocatorInstallation moduleLocatorInstallation;
  private final ModuleReleaseInstallation moduleReleaseInstallation;
  private final UploadedModuleInstallation uploadedModules;
  private final EnabledModuleManagement enabledModuleManagement;
  private final ManagedServiceQueries managedServiceQueries;
  private final DeclaredManagedServices declaredManagedServices;
  private final ModuleManagedServiceInstallation moduleManagedServiceInstallation;
  private final ManagedZalavaRestart managedZalavaRestart;

  public ModulesController(
      ZalavaRuntime zalavaRuntime,
      ModuleQueries moduleQueries,
      FileSystemModuleConfigurationStore moduleConfigurationStore,
      ModuleMarketplace marketplace,
      LocalDevelopmentProjectInstallation localDevelopmentProjects,
      ModuleLocatorInstallation moduleLocatorInstallation,
      ModuleReleaseInstallation moduleReleaseInstallation,
      UploadedModuleInstallation uploadedModules,
      EnabledModuleManagement enabledModuleManagement,
      ManagedServiceQueries managedServiceQueries,
      DeclaredManagedServices declaredManagedServices,
      ModuleManagedServiceInstallation moduleManagedServiceInstallation,
      ManagedZalavaRestart managedZalavaRestart) {
    this.zalavaRuntime = zalavaRuntime;
    this.moduleQueries = moduleQueries;
    this.moduleConfigurationStore = moduleConfigurationStore;
    this.marketplace = marketplace;
    this.localDevelopmentProjects = localDevelopmentProjects;
    this.moduleLocatorInstallation = moduleLocatorInstallation;
    this.moduleReleaseInstallation = moduleReleaseInstallation;
    this.uploadedModules = uploadedModules;
    this.enabledModuleManagement = enabledModuleManagement;
    this.managedServiceQueries = managedServiceQueries;
    this.declaredManagedServices = declaredManagedServices;
    this.moduleManagedServiceInstallation = moduleManagedServiceInstallation;
    this.managedZalavaRestart = managedZalavaRestart;
  }

  @GetMapping("/modules")
  public String modules(Model model, CsrfToken csrf) {
    model.addAttribute("model", buildModel());
    model.addAttribute("csrf", csrf);
    return "ui/modules";
  }

  @PostMapping("/modules/restart")
  public String restartSea(RedirectAttributes redirectAttributes) {
    try {
      ManagedZalavaRestart.Status status = managedZalavaRestart.request();
      redirectAttributes.addFlashAttribute("marketplaceMessage", status.message());
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute("marketplaceError", controlError(exception));
    }
    return "redirect:/modules";
  }

  @PostMapping("/modules/catalog/refresh")
  public String refreshCatalog(RedirectAttributes redirectAttributes) {
    try {
      ModuleMarketplace.Snapshot snapshot = marketplace.refresh();
      redirectAttributes.addFlashAttribute(
          "marketplaceMessage",
          "Catalog refreshed: " + snapshot.modules().size() + " module(s) available.");
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute("marketplaceError", controlError(exception));
    }
    return "redirect:/modules";
  }

  @PostMapping("/modules/upload-installations")
  public String createUploadedInstallation(
      @RequestParam("moduleJar") MultipartFile moduleJar, RedirectAttributes redirectAttributes) {
    try {
      if (moduleJar.isEmpty()) throw new IllegalArgumentException("Module JAR is required");
      ModuleReleaseInstallRequest request =
          uploadedModules.create(
              new UploadedModuleInstallation.Request(
                  moduleJar.getOriginalFilename(), moduleJar.getInputStream()));
      request = moduleReleaseInstallation.allow(request.requestId());
      redirectAttributes.addFlashAttribute(
          "marketplaceMessage",
          "Uploaded "
              + request.module().moduleId()
              + " "
              + request.module().version()
              + " installed ("
              + request.artifactDigest()
              + "). Restart Zalava once to load its classes.");
    } catch (java.io.IOException exception) {
      redirectAttributes.addFlashAttribute(
          "marketplaceError", "Unable to read uploaded module JAR.");
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute("marketplaceError", controlError(exception));
    }
    return "redirect:/modules";
  }

  @PostMapping("/modules/local-project-installations")
  public String createLocalProjectInstallation(
      @RequestParam String projectDirectory,
      @RequestParam String moduleId,
      @RequestParam String version,
      @RequestParam String developmentRequestId,
      RedirectAttributes redirectAttributes) {
    try {
      String requestedProjectDirectory =
          requireBounded(projectDirectory, "Project directory", 2000);
      String requestedModuleId = requireBounded(moduleId, "Module id", MAX_MODULE_ID_LENGTH);
      String requestedVersion = requireBounded(version, "Version", MAX_VERSION_LENGTH);
      String requestedDevelopmentRequestId =
          requireBounded(developmentRequestId, "Development request id", MAX_REQUEST_ID_LENGTH);
      localDevelopmentProjects.install(
          new LocalDevelopmentProjectInstallation.Request(
              requestedProjectDirectory,
              requestedModuleId,
              requestedVersion,
              requestedDevelopmentRequestId));
      redirectAttributes.addFlashAttribute(
          "marketplaceMessage",
          "Local module "
              + requestedModuleId
              + " "
              + requestedVersion
              + " installed. Restart Zalava once to load its classes.");
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute("marketplaceError", controlError(exception));
    }
    return "redirect:/modules";
  }

  @PostMapping("/modules/installation-requests")
  public String createInstallationRequest(
      @RequestParam String moduleId,
      @RequestParam String version,
      RedirectAttributes redirectAttributes) {
    try {
      String requestedModule = requireBounded(moduleId, "Module id", MAX_MODULE_ID_LENGTH);
      String requestedVersion = requireBounded(version, "Version", MAX_VERSION_LENGTH);
      marketplace.requireAvailable(requestedModule, requestedVersion);
      ModuleReleaseInstallRequest created =
          moduleLocatorInstallation.create(
              new ModuleLocatorInstallation.Request(requestedModule, requestedVersion, null));
      ModuleReleaseInstallRequest installed = moduleReleaseInstallation.allow(created.requestId());
      redirectAttributes.addFlashAttribute(
          "marketplaceMessage",
          "Installed "
              + requestedModule
              + " "
              + requestedVersion
              + " ("
              + installed.artifactDigest()
              + "). Restart Zalava once to load its classes.");
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute("marketplaceError", controlError(exception));
    }
    return "redirect:/modules";
  }

  @PostMapping("/modules/installation-requests/{requestId}/allow")
  public String allowInstallationRequest(
      @PathVariable String requestId, RedirectAttributes redirectAttributes) {
    return decideInstallationRequest(requestId, true, redirectAttributes);
  }

  @PostMapping("/modules/installation-requests/{requestId}/deny")
  public String denyInstallationRequest(
      @PathVariable String requestId, RedirectAttributes redirectAttributes) {
    return decideInstallationRequest(requestId, false, redirectAttributes);
  }

  private String decideInstallationRequest(
      String requestId, boolean allow, RedirectAttributes redirectAttributes) {
    try {
      String requestedId = requireBounded(requestId, "Request id", MAX_REQUEST_ID_LENGTH);
      ModuleReleaseInstallRequest decided =
          allow
              ? moduleReleaseInstallation.allow(requestedId)
              : moduleReleaseInstallation.deny(requestedId);
      redirectAttributes.addFlashAttribute(
          "marketplaceMessage",
          decided.module().moduleId()
              + " "
              + decided.module().version()
              + ": "
              + decideMessage(decided));
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute("marketplaceError", controlError(exception));
    }
    return "redirect:/modules";
  }

  @PostMapping("/modules/{moduleId}/disable")
  public String disableModule(
      @PathVariable String moduleId, RedirectAttributes redirectAttributes) {
    try {
      String requested = requireBounded(moduleId, "Module id", MAX_MODULE_ID_LENGTH);
      EnabledModuleManagement.DisableOutcome result = enabledModuleManagement.disable(requested);
      redirectAttributes.addFlashAttribute(
          "marketplaceMessage",
          result.changed()
              ? "Disabled " + requested + ". Restart Zalava to unload it from this running process."
              : requested + " is not enabled; nothing changed.");
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute("marketplaceError", controlError(exception));
    }
    return "redirect:/modules";
  }

  @GetMapping("/modules/{moduleId}")
  public String moduleDetail(@PathVariable String moduleId, Model model, CsrfToken csrf) {
    Optional<ZalavaModule> loaded =
        zalavaRuntime.modules().stream()
            .filter(module -> module.descriptor().moduleId().equals(moduleId))
            .findFirst();
    Optional<ModuleQueries.EnabledModule> enabled =
        moduleQueries.enabledModules().stream()
            .filter(module -> module.moduleId().equals(moduleId))
            .findFirst();
    Optional<ModuleMarketplace.Module> catalogModule =
        marketplace.snapshot().modules().stream()
            .filter(module -> module.moduleId().equals(moduleId))
            .findFirst();
    if (loaded.isEmpty() && enabled.isEmpty() && catalogModule.isEmpty()) {
      throw new IllegalArgumentException("Unknown module: " + moduleId);
    }
    model.addAttribute(
        "model",
        detailModel(
            moduleId,
            loaded.orElse(null),
            enabled.orElse(null),
            catalogModule.orElse(null),
            (String) model.getAttribute("managedServiceError"),
            (String) model.getAttribute("moduleMessage"),
            (String) model.getAttribute("moduleError"),
            (String) model.getAttribute("configurationMessage"),
            (String) model.getAttribute("configurationError")));
    model.addAttribute("csrf", csrf);
    return "ui/module-detail";
  }

  @GetMapping("/modules/{moduleId}/configuration")
  public String configuration(
      @PathVariable String moduleId,
      @RequestParam(defaultValue = "false") boolean onboarding,
      Model model,
      CsrfToken csrf) {
    ZalavaModule module = module(moduleId);
    ModuleConfigurationSnapshot snapshot =
        moduleConfigurationStore
            .candidate(moduleId)
            .or(() -> moduleConfigurationStore.active(moduleId))
            .orElseGet(() -> emptySnapshot(module));
    model.addAttribute("csrf", csrf);
    model.addAttribute(
        "model",
        new ModuleConfigurationModel(
            module.descriptor().moduleId(),
            module.descriptor().displayName(),
            status(moduleId),
            ModuleConfigurationForm.fields(module.configuration(), snapshot.factories()),
            snapshot.secretReferences().size(),
            moduleConfigurationStore.candidate(moduleId).isPresent(),
            onboarding));
    return "redirect:/modules/" + moduleId + "#configuration";
  }

  @PostMapping("/modules/{moduleId}/configuration")
  public String saveConfiguration(
      @PathVariable String moduleId,
      @RequestParam Map<String, String> submitted,
      @RequestParam(defaultValue = "false") boolean onboarding,
      RedirectAttributes redirectAttributes) {
    ZalavaModule module = module(moduleId);
    try {
      ModuleConfigurationSnapshot previous =
          moduleConfigurationStore
              .candidate(moduleId)
              .or(() -> moduleConfigurationStore.active(moduleId))
              .orElseGet(() -> emptySnapshot(module));
      List<ModuleConfigurationForm.Field> fields =
          ModuleConfigurationForm.fields(module.configuration(), previous.factories());
      Map<String, Object> factories = ModuleConfigurationForm.document(fields, submitted);
      CONFIGURATION_VALIDATOR.validateDocument(module.configuration(), factories);
      Map<String, String> references = secretReferences(fields, factories);
      Map<String, String> secrets = existingSecrets(moduleId, references);
      replaceSecrets(fields, factories, submitted, secrets);
      moduleConfigurationStore.saveCandidate(
          new ModuleConfigurationSnapshot(
              moduleId,
              module.descriptor().version(),
              Integer.toHexString(module.configuration().jsonSchema().hashCode()),
              factories,
              references),
          secrets);
      if (zalavaRuntime instanceof ManagedZalavaRuntime managedRuntime) {
        managedRuntime.applyCandidate(moduleId);
        redirectAttributes.addFlashAttribute(
            "configurationMessage", "Configuration applied. Start the module when ready.");
      } else {
        redirectAttributes.addFlashAttribute("configurationMessage", "Configuration saved.");
      }
    } catch (IllegalArgumentException exception) {
      redirectAttributes.addFlashAttribute("configurationError", exception.getMessage());
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute(
          "configurationError",
          "Module configuration could not be activated. Previous settings remain active.");
    }
    return onboarding && !redirectAttributes.getFlashAttributes().containsKey("configurationError")
        ? "redirect:/onboarding/starters"
        : "redirect:/modules/" + moduleId + "#configuration";
  }

  @PostMapping("/modules/{moduleId}/start")
  public String startModule(@PathVariable String moduleId, RedirectAttributes redirectAttributes) {
    try {
      ((ManagedZalavaRuntime) zalavaRuntime).start(moduleId);
      redirectAttributes.addFlashAttribute("moduleMessage", "Module started.");
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute(
          "moduleError", "Module could not start. Review its configuration and Zalava logs.");
    }
    return "redirect:/modules/" + moduleId;
  }

  @PostMapping("/modules/{moduleId}/stop")
  public String stopModule(@PathVariable String moduleId, RedirectAttributes redirectAttributes) {
    try {
      ((ManagedZalavaRuntime) zalavaRuntime).stop(moduleId);
      redirectAttributes.addFlashAttribute("moduleMessage", "Module stopped.");
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute(
          "moduleError", "Module could not stop while required by active services.");
    }
    return "redirect:/modules/" + moduleId;
  }

  @PostMapping("/modules/{moduleId}/managed-services/request")
  public String requestManagedServiceInstallation(
      @PathVariable String moduleId, RedirectAttributes redirectAttributes) {
    module(moduleId);
    try {
      moduleManagedServiceInstallation.requestDeclaredInstall(moduleId);
      moduleManagedServiceInstallation.approveLatest(moduleId);
      redirectAttributes.addFlashAttribute(
          "moduleMessage", "Managed service installation started.");
    } catch (RuntimeException exception) {
      redirectAttributes.addFlashAttribute("managedServiceError", exception.getMessage());
    }
    return "redirect:/modules/" + moduleId;
  }

  @PostMapping("/modules/{moduleId}/managed-services/approve")
  public String approveManagedServiceInstallation(
      @PathVariable String moduleId, RedirectAttributes redirectAttributes) {
    module(moduleId);
    try {
      moduleManagedServiceInstallation.approveLatest(moduleId);
    } catch (IllegalArgumentException | ManagedServiceInstallException exception) {
      redirectAttributes.addFlashAttribute("managedServiceError", exception.getMessage());
    }
    return "redirect:/modules/" + moduleId;
  }

  @PostMapping("/modules/{moduleId}/managed-services/deny")
  public String denyManagedServiceInstallation(
      @PathVariable String moduleId, RedirectAttributes redirectAttributes) {
    module(moduleId);
    try {
      moduleManagedServiceInstallation.denyLatest(moduleId);
    } catch (IllegalArgumentException | ManagedServiceInstallException exception) {
      redirectAttributes.addFlashAttribute("managedServiceError", exception.getMessage());
    }
    return "redirect:/modules/" + moduleId;
  }

  @PostMapping("/modules/{moduleId}/configuration/cancel")
  public String cancelConfiguration(
      @PathVariable String moduleId,
      @RequestParam(defaultValue = "false") boolean onboarding,
      RedirectAttributes redirectAttributes) {
    module(moduleId);
    moduleConfigurationStore.cancelCandidate(moduleId);
    redirectAttributes.addFlashAttribute(
        "configurationMessage", "Pending configuration changes discarded.");
    return onboarding
        ? "redirect:/onboarding/starters"
        : "redirect:/modules/" + moduleId + "#configuration";
  }

  private ModulesModel buildModel() {
    Map<String, ModuleQueries.EnabledModule> enabled =
        moduleQueries.enabledModules().stream()
            .collect(
                Collectors.toMap(
                    ModuleQueries.EnabledModule::moduleId,
                    Function.identity(),
                    (first, ignored) -> first));
    Map<String, Long> providerCounts =
        zalavaRuntime.loadedProviders().stream()
            .collect(
                Collectors.groupingBy(
                    loadedProvider -> loadedProvider.module().moduleId(), Collectors.counting()));

    List<ModuleEntry> loadedModules =
        zalavaRuntime.modules().stream()
            .map(
                module -> {
                  ModuleQueries.EnabledModule enabledModule =
                      enabled.get(module.descriptor().moduleId());
                  return toEntry(
                      module.descriptor(),
                      providerCounts,
                      enabledModule,
                      enabledModule != null || isExternal(module),
                      configurationStatus(module));
                })
            .sorted(Comparator.comparing(ModuleEntry::moduleId))
            .toList();
    List<EnabledModuleEntry> enabledModules =
        enabled.values().stream()
            .map(ModulesController::toEnabledEntry)
            .sorted(Comparator.comparing(EnabledModuleEntry::moduleId))
            .toList();
    List<CatalogModuleEntry> catalogModules =
        marketplace.snapshot().modules().stream()
            .map(
                module ->
                    new CatalogModuleEntry(
                        module.moduleId(),
                        module.displayName(),
                        module.description(),
                        enabled.containsKey(module.moduleId()),
                        Optional.ofNullable(enabled.get(module.moduleId()))
                            .map(ModuleQueries.EnabledModule::version)
                            .orElse(null)))
            .toList();
    List<InstallationRequestEntry> installationRequests =
        moduleReleaseInstallation.recent(RECENT_INSTALLATION_LIMIT).stream()
            .map(ModulesController::toInstallationRequestEntry)
            .toList();
    ManagedZalavaRestart.Status restart = managedZalavaRestart.status();

    return new ModulesModel(
        loadedModules,
        enabledModules,
        catalogModules,
        marketplace.snapshot().refreshedAt(),
        installationRequests,
        new RestartEntry(restart.availability().name(), restart.phase().name(), restart.message()));
  }

  private ModuleDetailModel detailModel(
      String moduleId,
      ZalavaModule loaded,
      ModuleQueries.EnabledModule enabled,
      ModuleMarketplace.Module catalogModule,
      String managedServiceError,
      String moduleMessage,
      String moduleError,
      String configurationMessage,
      String configurationError) {
    ConfigurationHealth health = loaded != null ? configurationStatus(loaded) : status(moduleId);
    ModuleConfigurationSnapshot configurationSnapshot =
        loaded == null
            ? null
            : moduleConfigurationStore
                .candidate(moduleId)
                .or(() -> moduleConfigurationStore.active(moduleId))
                .orElseGet(() -> emptySnapshot(loaded));
    List<ModuleConfigurationForm.Field> configurationFields =
        loaded == null
            ? List.of()
            : ModuleConfigurationForm.fields(
                loaded.configuration(), configurationSnapshot.factories());
    String lifecycleState =
        loaded == null
            ? enabled == null ? "AVAILABLE" : "AWAITING_RESTART"
            : zalavaRuntime instanceof ManagedZalavaRuntime managedRuntime
                ? managedRuntime.state(moduleId).state().name()
                : "RUNNING";
    String version =
        enabled != null ? enabled.version() : loaded != null ? loaded.descriptor().version() : null;
    String displayName =
        loaded != null
            ? loaded.descriptor().displayName()
            : catalogModule != null ? catalogModule.displayName() : moduleId;
    String description =
        loaded != null
            ? loaded.descriptor().description()
            : catalogModule != null ? catalogModule.description() : null;
    String provenance =
        enabled != null
            ? provenance(enabled)
            : loaded != null && isExternal(loaded)
                ? "External module (restart to unload)"
                : "Bundled with Zalava";
    List<String> permissions = enabled != null ? enabled.declaredPermissions() : List.of();
    List<ProviderEntry> providers =
        zalavaRuntime.loadedProviders().stream()
            .filter(provider -> provider.module().moduleId().equals(moduleId))
            .map(ModulesController::toProviderEntry)
            .sorted(Comparator.comparing(ProviderEntry::providerId))
            .toList();

    List<ManagedServiceEntry> managedServices = managedServiceEntries(moduleId);
    boolean hasDeclaredManagedServices =
        managedServices.stream().anyMatch(ManagedServiceEntry::declared);
    String managedServiceRequestStatus =
        moduleManagedServiceInstallation
            .latestForModule(moduleId)
            .map(request -> request.status().name())
            .orElse(null);

    boolean catalogAvailable =
        marketplace.snapshot().modules().stream()
            .anyMatch(module -> module.moduleId().equals(moduleId));
    List<CatalogReleaseEntry> releases = List.of();
    String latestVersion = null;
    boolean updateAvailable = false;
    if (catalogAvailable) {
      try {
        releases =
            marketplace.releases(moduleId).stream()
                .map(release -> new CatalogReleaseEntry(release.version(), release.releaseTag()))
                .toList();
        latestVersion = releases.isEmpty() ? null : releases.getFirst().version();
        updateAvailable =
            version != null
                && latestVersion != null
                && ModuleReleaseVersion.isNewer(latestVersion, version);
      } catch (RuntimeException exception) {
        catalogAvailable = false;
      }
    }

    return new ModuleDetailModel(
        moduleId,
        displayName,
        description,
        version,
        health.label(),
        health.cssClass(),
        provenance,
        permissions,
        health,
        configurationFields,
        configurationSnapshot == null ? 0 : configurationSnapshot.secretReferences().size(),
        loaded != null && moduleConfigurationStore.candidate(moduleId).isPresent(),
        loaded != null,
        enabled != null,
        catalogAvailable,
        latestVersion,
        updateAvailable,
        releases,
        providers,
        managedServices,
        hasDeclaredManagedServices,
        managedServiceRequestStatus,
        managedServiceError,
        lifecycleState,
        moduleMessage,
        moduleError,
        configurationMessage,
        configurationError);
  }

  private static ModuleEntry toEntry(
      ModuleDescriptor descriptor,
      Map<String, Long> providerCounts,
      ModuleQueries.EnabledModule enabled,
      boolean external,
      ConfigurationHealth configuration) {
    String statusLabel =
        enabled != null ? "Enabled external" : external ? "Disabled external" : "Built in";
    String statusClass =
        enabled != null ? "is-info is-light" : external ? "is-warning is-light" : "is-light";
    String provenance =
        enabled != null
            ? provenance(enabled)
            : external ? "External module (restart to unload)" : "Bundled with Zalava";
    return new ModuleEntry(
        descriptor.moduleId(),
        descriptor.displayName(),
        descriptor.version(),
        descriptor.description(),
        providerCounts.getOrDefault(descriptor.moduleId(), 0L),
        statusLabel,
        statusClass,
        provenance,
        configuration.label(),
        configuration.cssClass());
  }

  private static boolean isExternal(ZalavaModule module) {
    ClassLoader loader = module.getClass().getClassLoader();
    return loader != null && loader != ModulesController.class.getClassLoader();
  }

  private static EnabledModuleEntry toEnabledEntry(ModuleQueries.EnabledModule module) {
    return new EnabledModuleEntry(
        module.moduleId(),
        module.version(),
        module.sourceRepository(),
        module.binaryRepositoryId(),
        module.declaredPermissions());
  }

  private static InstallationRequestEntry toInstallationRequestEntry(
      ModuleReleaseInstallRequest request) {
    return new InstallationRequestEntry(
        request.requestId(),
        request.module().moduleId(),
        request.module().version(),
        request.artifactDigest(),
        request.repositoryId(),
        request.status().name(),
        statusClass(request.status()),
        request.message(),
        request.decidedAt() == null ? null : request.decidedAt().toString(),
        request.status() == ModuleReleaseInstallRequest.Status.PENDING);
  }

  private static String statusClass(ModuleReleaseInstallRequest.Status status) {
    return switch (status) {
      case PENDING -> "is-warning is-light";
      case SUCCEEDED -> "is-success is-light";
      case DENIED -> "is-light";
      case FAILED -> "is-danger is-light";
    };
  }

  private static String decideMessage(ModuleReleaseInstallRequest request) {
    String message = request.message();
    if (message == null || message.isBlank()) {
      return request.status().name().toLowerCase();
    }
    return message;
  }

  private static ProviderEntry toProviderEntry(LoadedZalavaProvider loadedProvider) {
    ProviderDescriptor provider = loadedProvider.provider().descriptor();
    List<ToolEntry> tools =
        loadedProvider.provider().listTools().stream()
            .map(ModulesController::toToolEntry)
            .sorted(Comparator.comparing(ToolEntry::name))
            .toList();
    return new ProviderEntry(
        provider.providerId(),
        provider.displayName(),
        provider.providerType(),
        provider.description(),
        provider.policyTags(),
        provider.scope().entrySet().stream()
            .map(entry -> new ScopeEntry(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(ScopeEntry::name))
            .toList(),
        tools);
  }

  private static ToolEntry toToolEntry(ZalavaToolDescriptor tool) {
    return new ToolEntry(tool.name(), tool.description(), tool.sideEffecting());
  }

  private List<ManagedServiceEntry> managedServiceEntries(String moduleId) {
    Map<String, ManagedServiceQueries.ManagedServiceSummary> installed = new LinkedHashMap<>();
    for (ManagedServiceQueries.ManagedServiceSummary summary :
        managedServiceQueries.forModule(moduleId)) {
      installed.put(summary.serviceId(), summary);
    }
    Map<String, ManagedServiceDeclaration> declared = new LinkedHashMap<>();
    for (ManagedServiceDeclaration declaration : declaredManagedServices.forModule(moduleId)) {
      declared.put(declaration.serviceId(), declaration);
    }
    Set<String> serviceIds = new TreeSet<>();
    serviceIds.addAll(installed.keySet());
    serviceIds.addAll(declared.keySet());
    return serviceIds.stream()
        .map(
            serviceId ->
                toManagedServiceEntry(serviceId, installed.get(serviceId), declared.get(serviceId)))
        .toList();
  }

  private static ManagedServiceEntry toManagedServiceEntry(
      String serviceId,
      ManagedServiceQueries.ManagedServiceSummary installed,
      ManagedServiceDeclaration declared) {
    Set<Integer> ports =
        installed != null
            ? installed.ports()
            : declared != null ? declared.desiredState().ports() : Set.of();
    List<String> endpoints =
        ports.stream().sorted().map(port -> "http://127.0.0.1:" + port).toList();
    String observedState = installed != null ? installed.observedState() : "DECLARED";
    String artifactReference =
        installed != null
            ? installed.artifactReference()
            : declared != null ? declared.desiredState().artifactReference() : null;
    int consecutiveFailures = installed != null ? installed.consecutiveFailures() : 0;
    return new ManagedServiceEntry(
        serviceId,
        managedServiceStateLabel(observedState),
        managedServiceStateClass(observedState),
        artifactReference,
        endpoints,
        consecutiveFailures,
        declared != null && installed == null);
  }

  private static String managedServiceStateLabel(String observedState) {
    return switch (observedState) {
      case "RUNNING" -> "Running";
      case "STARTING" -> "Starting";
      case "STOPPED" -> "Stopped";
      case "FAILED" -> "Failed";
      case "ABSENT" -> "Not installed";
      case "DECLARED" -> "Declared, awaiting administrator approval";
      default -> observedState;
    };
  }

  private static String managedServiceStateClass(String observedState) {
    return switch (observedState) {
      case "RUNNING" -> "is-success is-light";
      case "STARTING" -> "is-warning is-light";
      case "FAILED" -> "is-danger is-light";
      case "DECLARED" -> "is-warning is-light";
      default -> "is-light";
    };
  }

  private static String provenance(ModuleQueries.EnabledModule module) {
    if (hasText(module.sourceRepository())) {
      return module.sourceRepository();
    }
    if (hasText(module.binaryRepositoryId())) {
      return module.binaryRepositoryId();
    }
    return "Local artifact";
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private ZalavaModule module(String moduleId) {
    return zalavaRuntime.modules().stream()
        .filter(candidate -> candidate.descriptor().moduleId().equals(moduleId))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unknown module: " + moduleId));
  }

  private ModuleConfigurationSnapshot emptySnapshot(ZalavaModule module) {
    return new ModuleConfigurationSnapshot(
        module.descriptor().moduleId(),
        module.descriptor().version(),
        Integer.toHexString(module.configuration().jsonSchema().hashCode()),
        Map.of(),
        Map.of());
  }

  private ConfigurationHealth configurationStatus(ZalavaModule module) {
    if (module.configuration().jsonSchema().get("properties") instanceof Map<?, ?> properties
        && !properties.isEmpty()) {
      return status(module.descriptor().moduleId());
    }
    return new ConfigurationHealth("No configuration", "is-light");
  }

  private ConfigurationHealth status(String moduleId) {
    return switch (moduleConfigurationStore.status(moduleId)) {
      case ACTIVE -> new ConfigurationHealth("Active", "is-success is-light");
      case RESTART_REQUIRED -> new ConfigurationHealth("Changes pending", "is-warning is-light");
      case CONFIGURATION_INVALID -> new ConfigurationHealth("Needs recovery", "is-danger is-light");
      case ACTIVATION_FAILED -> new ConfigurationHealth("Activation failed", "is-danger is-light");
      case SETUP_REQUIRED -> new ConfigurationHealth("Setup required", "is-warning is-light");
    };
  }

  private static String requireBounded(String value, String name, int maximumLength) {
    if (value == null || value.isBlank() || value.length() > maximumLength) {
      throw new IllegalArgumentException(
          name + " must contain between 1 and " + maximumLength + " characters");
    }
    return value;
  }

  private static String controlError(RuntimeException exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) {
      return "Module management operation failed";
    }
    String normalized = message.replaceAll("\\s+", " ").trim();
    return normalized.length() <= MAX_ERROR_LENGTH
        ? normalized
        : normalized.substring(0, MAX_ERROR_LENGTH) + "...";
  }

  private Map<String, String> secretReferences(
      List<ModuleConfigurationForm.Field> fields, Map<String, Object> factories) {
    Map<String, String> references = new HashMap<>();
    for (ModuleConfigurationForm.Field field : fields) {
      if (field.secretReference()) {
        Object value = valueAt(factories, field.path());
        if (value instanceof String reference && !reference.isBlank()) {
          references.put(field.path(), reference);
        }
      }
    }
    return Map.copyOf(references);
  }

  private Map<String, String> existingSecrets(String moduleId, Map<String, String> references) {
    Map<String, String> values = new HashMap<>();
    for (String reference : references.values()) {
      moduleConfigurationStore
          .candidateSecrets(moduleId)
          .resolve(reference)
          .or(() -> moduleConfigurationStore.secrets(moduleId).resolve(reference))
          .ifPresent(value -> values.put(reference, new String(value)));
    }
    return values;
  }

  private static void replaceSecrets(
      List<ModuleConfigurationForm.Field> fields,
      Map<String, Object> factories,
      Map<String, String> submitted,
      Map<String, String> secrets) {
    for (ModuleConfigurationForm.Field field : fields) {
      String replacement = submitted.get(field.replacementSecretName());
      Object reference = valueAt(factories, field.path());
      if (field.secretReference()
          && replacement != null
          && !replacement.isBlank()
          && reference instanceof String key) {
        secrets.put(key, replacement);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static Object valueAt(Map<String, Object> values, String path) {
    Object current = values;
    for (String segment : path.split("\\.")) {
      if (!(current instanceof Map<?, ?> map)) {
        return null;
      }
      current = ((Map<String, Object>) map).get(segment);
    }
    return current;
  }

  public record ModulesModel(
      List<ModuleEntry> loadedModules,
      List<EnabledModuleEntry> enabledModules,
      List<CatalogModuleEntry> catalogModules,
      String catalogRefreshedAt,
      List<InstallationRequestEntry> installationRequests,
      RestartEntry restart) {}

  public record RestartEntry(String availability, String phase, String message) {
    public boolean available() {
      return "AVAILABLE".equals(availability);
    }

    public boolean requested() {
      return "REQUESTED".equals(phase);
    }
  }

  public record ModuleEntry(
      String moduleId,
      String displayName,
      String version,
      String description,
      long providerCount,
      String statusLabel,
      String statusClass,
      String provenance,
      String configurationLabel,
      String configurationClass) {}

  public record EnabledModuleEntry(
      String moduleId,
      String version,
      String sourceRepository,
      String binaryRepositoryId,
      List<String> declaredPermissions) {}

  public record CatalogModuleEntry(
      String moduleId,
      String displayName,
      String description,
      boolean installed,
      String installedVersion) {}

  public record CatalogReleaseEntry(String version, String releaseTag) {}

  public record InstallationRequestEntry(
      String requestId,
      String moduleId,
      String version,
      String artifactDigest,
      String repositoryId,
      String status,
      String statusClass,
      String message,
      String decidedAt,
      boolean pending) {}

  public record ModuleDetailModel(
      String moduleId,
      String displayName,
      String description,
      String version,
      String statusLabel,
      String statusClass,
      String provenance,
      List<String> declaredPermissions,
      ConfigurationHealth configuration,
      List<ModuleConfigurationForm.Field> configurationFields,
      int storedSecretCount,
      boolean candidatePresent,
      boolean loaded,
      boolean enabledExternal,
      boolean catalogAvailable,
      String latestVersion,
      boolean updateAvailable,
      List<CatalogReleaseEntry> releases,
      List<ProviderEntry> providers,
      List<ManagedServiceEntry> managedServices,
      boolean hasDeclaredManagedServices,
      String managedServiceRequestStatus,
      String managedServiceError,
      String lifecycleState,
      String moduleMessage,
      String moduleError,
      String configurationMessage,
      String configurationError) {}

  public record ManagedServiceEntry(
      String serviceId,
      String stateLabel,
      String stateClass,
      String artifactReference,
      List<String> endpoints,
      int consecutiveFailures,
      boolean declared) {}

  public record ProviderEntry(
      String providerId,
      String displayName,
      String providerType,
      String description,
      List<String> policyTags,
      List<ScopeEntry> scope,
      List<ToolEntry> tools) {}

  public record ScopeEntry(String name, String value) {}

  public record ToolEntry(String name, String description, boolean sideEffecting) {}

  public record ModuleConfigurationModel(
      String moduleId,
      String displayName,
      ConfigurationHealth configuration,
      List<ModuleConfigurationForm.Field> fields,
      int storedSecretCount,
      boolean candidatePresent,
      boolean onboarding) {}

  public record ConfigurationHealth(String label, String cssClass) {}
}
