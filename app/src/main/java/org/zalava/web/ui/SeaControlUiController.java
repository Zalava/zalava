package org.zalava.web.ui;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.zalava.ModuleDescriptor;
import org.zalava.ProviderDescriptor;
import org.zalava.ZalavaToolDescriptor;
import org.zalava.capabilities.approval.SeaToolApprovalRequests;
import org.zalava.modules.catalog.LocalArtifactInstallRequest;
import org.zalava.modules.catalog.LocalArtifactModuleMetadataLoader;
import org.zalava.modules.catalog.ModuleReleaseInstallRequest;
import org.zalava.modules.catalog.SourceModuleIndex;
import org.zalava.modules.catalog.install.application.ControlCatalogDiscovery;
import org.zalava.modules.catalog.install.application.port.in.LocalArtifactModuleInstallation;
import org.zalava.modules.catalog.install.application.port.in.LocalModuleProjectInstallation;
import org.zalava.modules.catalog.install.application.port.in.ModuleLocatorInstallation;
import org.zalava.modules.catalog.install.application.port.in.ModuleReleaseInstallation;
import org.zalava.modules.development.DevelopmentRequestStatus;
import org.zalava.modules.development.ModuleDevelopmentRequest;
import org.zalava.modules.runtime.LoadedSeaProvider;
import org.zalava.modules.runtime.SeaRuntime;
import org.zalava.web.control.adapter.in.http.SeaBootstrapVerificationService;
import org.zalava.web.control.application.AdministratorControlAuthorization;
import org.zalava.web.control.application.port.in.InvocationLogQueries;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Controller
@RequestMapping(SeaControlUiController.PATH)
public class SeaControlUiController {

  public static final String PATH = "/sea/control";

  public static final String WORKSPACE_PATH = "/workspace";

  private static final String MODEL_ATTRIBUTE = "model";

  private static final String INDEX_TEMPLATE = "sea/control/index";

  private static final String WORKSPACE_TEMPLATE = "sea/control/workspace";

  private static final int RECENT_INSTALLATION_LIMIT = 20;

  private static final int MAX_INDEX_YAML_LENGTH = 200_000;

  private static final int MAX_MODULE_ID_LENGTH = 160;

  private static final int MAX_RELEASE_FIELD_LENGTH = 2_000;

  private static final int MAX_ERROR_LENGTH = 300;

  private static final int MAX_POLICY_SCOPE_KEY_LENGTH = 100;

  private static final int MAX_POLICY_SCOPE_VALUE_LENGTH = 500;

  private static final int MAX_POLICY_EXPIRY_LENGTH = 64;

  private static final ObjectMapper JSON = new ObjectMapper();

  private static final LocalArtifactModuleMetadataLoader LOCAL_ARTIFACT_METADATA_LOADER =
      new LocalArtifactModuleMetadataLoader();

  private final SeaRuntime seaRuntime;
  private final SeaBootstrapVerificationService verificationService;
  private final AdministratorControlAuthorization authorization;
  private final InvocationLogQueries invocationLog;
  private final SeaToolApprovalRequests permissionRequests;
  private final LocalArtifactModuleInstallation localArtifactModuleInstallation;

  private final ModuleReleaseInstallation moduleReleaseInstallation;
  private final ModuleLocatorInstallation moduleLocatorInstallation;
  private final LocalModuleProjectInstallation localProjects;
  private final CatalogSelection catalogSelection;

  SeaControlUiController(
      SeaRuntime seaRuntime,
      SeaBootstrapVerificationService verificationService,
      AdministratorControlAuthorization authorization,
      InvocationLogQueries invocationLog,
      SeaToolApprovalRequests permissionRequests,
      LocalArtifactModuleInstallation localArtifactModuleInstallation,
      ModuleReleaseInstallation moduleReleaseInstallation,
      ModuleLocatorInstallation moduleLocatorInstallation,
      LocalModuleProjectInstallation localProjects,
      ControlCatalogDiscovery catalogDiscovery) {
    this.seaRuntime = seaRuntime;
    this.verificationService = verificationService;
    this.authorization = authorization;
    this.invocationLog = invocationLog;
    this.permissionRequests = permissionRequests;
    this.localArtifactModuleInstallation = localArtifactModuleInstallation;
    this.moduleReleaseInstallation = moduleReleaseInstallation;
    this.moduleLocatorInstallation = moduleLocatorInstallation;
    this.localProjects = localProjects;
    this.catalogSelection = new CatalogSelection(catalogDiscovery);
  }

  @GetMapping
  String index(Model model) {
    model.addAttribute(MODEL_ATTRIBUTE, buildModel(null));
    return INDEX_TEMPLATE;
  }

  @GetMapping(WORKSPACE_PATH)
  String workspace(Model model) {
    model.addAttribute(MODEL_ATTRIBUTE, buildModel(null));
    return WORKSPACE_TEMPLATE;
  }

  @PostMapping("/local-module-installations")
  String createLocalModuleInstallation(
      @RequestParam String moduleId,
      @RequestParam String indexYaml,
      @RequestParam String artifactPath,
      @RequestParam String developmentRequestId,
      Model model) {
    String error = null;
    try {
      requireInstallationInput(moduleId, indexYaml);
      SourceModuleIndex.Module module =
          LOCAL_ARTIFACT_METADATA_LOADER.load(indexYaml).modules().stream()
              .filter(candidate -> candidate.moduleId().equals(moduleId))
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "Module id is not present in the validated index: " + moduleId));
      localArtifactModuleInstallation.create(
          module,
          artifactPath,
          new org.zalava.modules.development.DevelopmentRequestId(developmentRequestId));
    } catch (RuntimeException ex) {
      error = controlError(ex);
    }
    model.addAttribute(MODEL_ATTRIBUTE, buildModel(error));
    return WORKSPACE_TEMPLATE;
  }

  @PostMapping("/local-module-project-installations")
  String createLocalModuleProjectInstallation(
      @RequestParam String projectDirectory,
      @RequestParam String moduleId,
      @RequestParam String version,
      @RequestParam(required = false) String developmentRequestId,
      Model model) {
    String error = null;
    try {
      localProjects.create(
          new LocalModuleProjectInstallation.Request(
              projectDirectory,
              requireReleaseField(moduleId, "Module id"),
              requireReleaseField(version, "Version"),
              developmentRequestId));
    } catch (RuntimeException ex) {
      error = controlError(ex);
    }
    model.addAttribute(MODEL_ATTRIBUTE, buildModel(error));
    return WORKSPACE_TEMPLATE;
  }

  @PostMapping("/local-module-installations/{requestId}/allow")
  String allowLocalModuleInstallation(@PathVariable String requestId, Model model) {
    return decideLocalModuleInstallation(requestId, true, model);
  }

  @PostMapping("/local-module-installations/{requestId}/deny")
  String denyLocalModuleInstallation(@PathVariable String requestId, Model model) {
    return decideLocalModuleInstallation(requestId, false, model);
  }

  @PostMapping("/module-release-installations")
  String createModuleReleaseInstallation(
      @RequestParam String moduleId,
      @RequestParam String version,
      @RequestParam(required = false) String developmentRequestId,
      Model model) {
    String error = null;
    try {
      catalogSelection.requireAvailable(moduleId, version);
      moduleLocatorInstallation.create(
          new ModuleLocatorInstallation.Request(
              requireReleaseField(moduleId, "Module id"),
              requireReleaseField(version, "Version"),
              developmentRequestId == null || developmentRequestId.isBlank()
                  ? null
                  : requireReleaseField(developmentRequestId, "Development request id")));
    } catch (RuntimeException ex) {
      error = controlError(ex);
    }
    model.addAttribute(MODEL_ATTRIBUTE, buildModel(error));
    return WORKSPACE_TEMPLATE;
  }

  @PostMapping("/module-release-installations/catalog/refresh")
  String refreshCatalog(Model model) {
    String error = null;
    try {
      catalogSelection.refresh();
    } catch (RuntimeException ex) {
      error = controlError(ex);
    }
    model.addAttribute(MODEL_ATTRIBUTE, buildModel(error));
    return WORKSPACE_TEMPLATE;
  }

  @PostMapping("/module-release-installations/catalog/select")
  String selectCatalogModule(@RequestParam String moduleId, Model model) {
    String error = null;
    try {
      catalogSelection.select(requireReleaseField(moduleId, "Catalog module"));
    } catch (RuntimeException ex) {
      error = controlError(ex);
    }
    model.addAttribute(MODEL_ATTRIBUTE, buildModel(error));
    return WORKSPACE_TEMPLATE;
  }

  @PostMapping("/module-release-installations/{requestId}/allow")
  String allowModuleReleaseInstallation(@PathVariable String requestId, Model model) {
    return decideModuleReleaseInstallation(requestId, true, model);
  }

  @PostMapping("/module-release-installations/{requestId}/deny")
  String denyModuleReleaseInstallation(@PathVariable String requestId, Model model) {
    return decideModuleReleaseInstallation(requestId, false, model);
  }

  private String decideModuleReleaseInstallation(String requestId, boolean allow, Model model) {
    String error = null;
    try {
      if (allow) moduleReleaseInstallation.allow(requestId);
      else moduleReleaseInstallation.deny(requestId);
    } catch (RuntimeException ex) {
      error = controlError(ex);
    }
    model.addAttribute(MODEL_ATTRIBUTE, buildModel(error));
    return WORKSPACE_TEMPLATE;
  }

  private String decideLocalModuleInstallation(String requestId, boolean allow, Model model) {
    String error = null;
    try {
      if (allow) localArtifactModuleInstallation.allow(requestId);
      else localArtifactModuleInstallation.deny(requestId);
    } catch (RuntimeException ex) {
      error = controlError(ex);
    }
    model.addAttribute(MODEL_ATTRIBUTE, buildModel(error));
    return WORKSPACE_TEMPLATE;
  }

  private static void requireInstallationInput(String moduleId, String indexYaml) {
    if (moduleId == null || moduleId.isBlank() || moduleId.length() > MAX_MODULE_ID_LENGTH) {
      throw new IllegalArgumentException("Module id must contain between 1 and 160 characters");
    }
    if (indexYaml == null || indexYaml.isBlank()) {
      throw new IllegalArgumentException("Source module index YAML is required");
    }
    if (indexYaml.length() > MAX_INDEX_YAML_LENGTH) {
      throw new IllegalArgumentException("Source module index YAML exceeds 200000 characters");
    }
  }

  private static String requireReleaseField(String value, String name) {
    if (value == null || value.isBlank() || value.length() > MAX_RELEASE_FIELD_LENGTH) {
      throw new IllegalArgumentException(
          name + " must contain between 1 and " + MAX_RELEASE_FIELD_LENGTH + " characters");
    }
    return value;
  }

  private static URI requireHttpsUri(String value, String name) {
    String candidate = requireReleaseField(value, name);
    URI uri;
    try {
      uri = URI.create(candidate);
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(name + " must be a valid HTTPS URI");
    }
    if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
      throw new IllegalArgumentException(name + " must use HTTPS");
    }
    return uri;
  }

  @PostMapping("/permission-policies/{requestId}/revoke")
  String revokePermissionPolicy(@PathVariable String requestId, Model model) {
    String error = null;
    try {
      permissionRequests.revokeToolPolicy(requestId);
    } catch (RuntimeException ex) {
      error = controlError(ex);
    }
    model.addAttribute(MODEL_ATTRIBUTE, buildModel(error));
    return WORKSPACE_TEMPLATE;
  }

  @PostMapping("/permission-policies/{requestId}/narrow")
  String narrowPermissionPolicy(
      @PathVariable String requestId,
      @RequestParam(required = false) String scopeKey,
      @RequestParam(required = false) String scopeValue,
      @RequestParam(required = false) String expiresAt,
      Model model) {
    String error = null;
    try {
      String normalizedScopeKey =
          optionalPolicyField(scopeKey, "Scope key", MAX_POLICY_SCOPE_KEY_LENGTH);
      String normalizedScopeValue =
          optionalPolicyField(scopeValue, "Scope value", MAX_POLICY_SCOPE_VALUE_LENGTH);
      if ((normalizedScopeKey == null) != (normalizedScopeValue == null)) {
        throw new IllegalArgumentException("Scope key and value must be provided together");
      }
      var existing = permissionRequests.get(requestId);
      Map<String, String> narrowedScope = new LinkedHashMap<>(existing.scope());
      if (normalizedScopeKey != null) narrowedScope.put(normalizedScopeKey, normalizedScopeValue);
      String normalizedExpiry = optionalPolicyField(expiresAt, "Expiry", MAX_POLICY_EXPIRY_LENGTH);
      Instant expiry = normalizedExpiry == null ? null : Instant.parse(normalizedExpiry);
      permissionRequests.narrowToolPolicy(requestId, narrowedScope, expiry);
    } catch (RuntimeException ex) {
      error = controlError(ex);
    }
    model.addAttribute(MODEL_ATTRIBUTE, buildModel(error));
    return WORKSPACE_TEMPLATE;
  }

  private static String optionalPolicyField(String value, String name, int maximumLength) {
    if (value == null || value.isBlank()) return null;
    if (value.length() > maximumLength) {
      throw new IllegalArgumentException(
          name + " must contain at most " + maximumLength + " characters");
    }
    return value;
  }

  private SeaControlUiModel buildModel(String installationError) {
    return buildModel(installationError, null);
  }

  String renderWorkspace(Model model, String error, DevelopmentRequestEntry developmentRequest) {
    model.addAttribute(MODEL_ATTRIBUTE, buildModel(error, developmentRequest));
    return WORKSPACE_TEMPLATE;
  }

  private SeaControlUiModel buildModel(
      String installationError, DevelopmentRequestEntry developmentRequest) {
    return authorization.call(
        "sea-control", () -> assembleModel(installationError, developmentRequest));
  }

  private SeaControlUiModel assembleModel(
      String installationError, DevelopmentRequestEntry developmentRequest) {
    List<ModuleEntry> modules =
        seaRuntime.modules().stream()
            .map(SeaControlUiController::toModuleEntry)
            .sorted(Comparator.comparing(ModuleEntry::moduleId))
            .toList();
    List<ProviderEntry> providers =
        seaRuntime.loadedProviders().stream()
            .map(SeaControlUiController::toProviderEntry)
            .sorted(Comparator.comparing(ProviderEntry::providerId))
            .toList();
    List<VerificationEntry> verifications =
        verificationService.bootstrapVerification().stream()
            .map(SeaControlUiController::toVerificationEntry)
            .toList();
    int toolCount = providers.stream().mapToInt(provider -> provider.tools().size()).sum();
    long sideEffectingToolCount =
        providers.stream()
            .flatMap(provider -> provider.tools().stream())
            .filter(ToolEntry::sideEffecting)
            .count();
    return new SeaControlUiModel(
        Instant.now().toString(),
        modules,
        providers,
        verifications,
        modules.size(),
        providers.size(),
        toolCount,
        sideEffectingToolCount,
        permissionRequests.activeToolPolicies(),
        permissionRequests.recentEntries(),
        invocationLog.recentEntries(),
        localArtifactModuleInstallation.recent(RECENT_INSTALLATION_LIMIT),
        moduleReleaseInstallation.recent(RECENT_INSTALLATION_LIMIT),
        catalogSelection.snapshot(),
        installationError,
        developmentRequest);
  }

  private static String controlError(RuntimeException exception) {
    String message = exception.getMessage();
    if (message == null || message.isBlank()) {
      return "Source module installation operation failed";
    }
    String normalized = message.replaceAll("\\s+", " ").trim();
    return normalized.length() <= MAX_ERROR_LENGTH
        ? normalized
        : normalized.substring(0, MAX_ERROR_LENGTH) + "...";
  }

  private static ModuleEntry toModuleEntry(org.zalava.ZalavaModule module) {
    ModuleDescriptor descriptor = module.descriptor();
    return new ModuleEntry(
        descriptor.moduleId(),
        descriptor.displayName(),
        descriptor.version(),
        descriptor.description());
  }

  private static ProviderEntry toProviderEntry(LoadedSeaProvider loadedProvider) {
    ProviderDescriptor provider = loadedProvider.provider().descriptor();
    List<ToolEntry> tools =
        loadedProvider.provider().listTools().stream()
            .map(SeaControlUiController::toToolEntry)
            .sorted(Comparator.comparing(ToolEntry::name))
            .toList();
    return new ProviderEntry(
        provider.providerId(),
        provider.displayName(),
        provider.providerType(),
        provider.moduleId(),
        provider.description(),
        provider.policyTags(),
        provider.scope().entrySet().stream()
            .map(entry -> new ScopeEntry(entry.getKey(), entry.getValue()))
            .sorted(Comparator.comparing(ScopeEntry::name))
            .toList(),
        tools);
  }

  private static ToolEntry toToolEntry(ZalavaToolDescriptor tool) {
    return new ToolEntry(
        tool.name(),
        tool.description(),
        tool.sideEffecting(),
        classification(tool),
        tool.policyTags());
  }

  private static VerificationEntry toVerificationEntry(
      SeaBootstrapVerificationService.BootstrapToolVerification verification) {
    List<VerificationStepEntry> steps =
        verification.steps().stream().map(SeaControlUiController::toVerificationStepEntry).toList();
    return new VerificationEntry(
        verification.toolset(),
        verification.providerId(),
        verification.displayName(),
        verification.available(),
        verification.missingReason(),
        steps);
  }

  private static VerificationStepEntry toVerificationStepEntry(
      SeaBootstrapVerificationService.VerificationStep step) {
    return new VerificationStepEntry(
        step.label(),
        step.method(),
        step.path(),
        step.toolName(),
        step.sideEffecting(),
        step.confirmationRequired(),
        json(step.body()));
  }

  private static String classification(ZalavaToolDescriptor descriptor) {
    return "sea_backed";
  }

  private static String json(Map<String, Object> value) {
    try {
      return JSON.writerWithDefaultPrettyPrinter().writeValueAsString(value);
    } catch (JacksonException ex) {
      throw new IllegalStateException("Unable to render verification body", ex);
    }
  }

  public record SeaControlUiModel(
      String refreshedAt,
      List<ModuleEntry> modules,
      List<ProviderEntry> providers,
      List<VerificationEntry> verifications,
      int moduleCount,
      int providerCount,
      int toolCount,
      long sideEffectingToolCount,
      List<SeaToolApprovalRequests.Entry> permissionPolicies,
      List<SeaToolApprovalRequests.Entry> permissionRequests,
      List<InvocationLogQueries.Entry> invocationLogs,
      List<LocalArtifactInstallRequest> localArtifactInstallationRequests,
      List<ModuleReleaseInstallRequest> moduleReleaseInstallationRequests,
      CatalogInstallationEntry catalogInstallation,
      String installationError,
      DevelopmentRequestEntry developmentRequest) {}

  public record CatalogInstallationEntry(
      List<CatalogModuleEntry> modules,
      String selectedModuleId,
      List<CatalogReleaseEntry> releases,
      String refreshedAt) {
    static CatalogInstallationEntry empty() {
      return new CatalogInstallationEntry(List.of(), null, List.of(), null);
    }
  }

  public record CatalogModuleEntry(String moduleId, String displayName, String description) {}

  public record CatalogReleaseEntry(String version, String releaseTag) {}

  private static final class CatalogSelection {
    private final ControlCatalogDiscovery discovery;
    private CatalogInstallationEntry current = CatalogInstallationEntry.empty();

    private CatalogSelection(ControlCatalogDiscovery discovery) {
      this.discovery = discovery;
    }

    synchronized void refresh() {
      List<CatalogModuleEntry> modules =
          discovery.modules().stream()
              .map(
                  module ->
                      new CatalogModuleEntry(
                          module.moduleId(), module.displayName(), module.description()))
              .toList();
      current = new CatalogInstallationEntry(modules, null, List.of(), Instant.now().toString());
    }

    synchronized void select(String moduleId) {
      if (current.modules().stream().noneMatch(module -> module.moduleId().equals(moduleId))) {
        throw new IllegalArgumentException(
            "Catalog module is not available; refresh the catalog and choose a module");
      }
      List<CatalogReleaseEntry> releases =
          discovery.releases(moduleId).stream()
              .map(release -> new CatalogReleaseEntry(release.version(), release.releaseTag()))
              .toList();
      current =
          new CatalogInstallationEntry(
              current.modules(), moduleId, releases, current.refreshedAt());
    }

    synchronized void requireAvailable(String moduleId, String version) {
      if (!moduleId.equals(current.selectedModuleId())
          || current.releases().stream().noneMatch(release -> release.version().equals(version))) {
        throw new IllegalArgumentException(
            "Choose a module and release version from the refreshed catalog");
      }
    }

    synchronized CatalogInstallationEntry snapshot() {
      return current;
    }
  }

  public record DevelopmentRequestEntry(
      String requestId,
      String moduleId,
      String status,
      Integer revision,
      List<CandidateEntry> candidates,
      List<String> exportedFiles,
      String installationApproval) {
    static DevelopmentRequestEntry from(ModuleDevelopmentRequest request) {
      return new DevelopmentRequestEntry(
          request.id().value(),
          request.currentRevision().contract().module().moduleId(),
          request.status().name(),
          request.currentRevision().number(),
          request.candidateAttempts().stream().map(CandidateEntry::from).toList(),
          List.of(),
          request.status() == DevelopmentRequestStatus.READY_TO_INSTALL
              ? "required before installation"
              : "not available");
    }

    static DevelopmentRequestEntry exported(String requestId, List<String> files) {
      return new DevelopmentRequestEntry(
          requestId, null, "EXPORTED", null, List.of(), List.copyOf(files), "not available");
    }
  }

  public record CandidateEntry(
      int number,
      String digest,
      Boolean accepted,
      List<String> evidence,
      List<org.zalava.modules.development.CandidateEvaluation.Invocation> invocations,
      String jsonReport,
      String markdownReport) {
    static CandidateEntry from(ModuleDevelopmentRequest.CandidateAttempt attempt) {
      return new CandidateEntry(
          attempt.number(),
          attempt.sha256Digest(),
          attempt.evaluation() == null ? null : attempt.evaluation().accepted(),
          attempt.evaluation() == null ? List.of() : attempt.evaluation().evidence(),
          attempt.evaluation() == null ? List.of() : attempt.evaluation().invocations(),
          attempt.evaluation() == null ? null : attempt.evaluation().jsonReport(),
          attempt.evaluation() == null ? null : attempt.evaluation().markdownReport());
    }
  }

  public record ModuleEntry(
      String moduleId, String displayName, String version, String description) {}

  public record ProviderEntry(
      String providerId,
      String displayName,
      String providerType,
      String moduleId,
      String description,
      List<String> policyTags,
      List<ScopeEntry> scope,
      List<ToolEntry> tools) {}

  public record ScopeEntry(String name, String value) {}

  public record ToolEntry(
      String name,
      String description,
      boolean sideEffecting,
      String classification,
      List<String> policyTags) {}

  public record VerificationEntry(
      String toolset,
      String providerId,
      String displayName,
      boolean available,
      String missingReason,
      List<VerificationStepEntry> steps) {}

  public record VerificationStepEntry(
      String label,
      String method,
      String path,
      String toolName,
      boolean sideEffecting,
      boolean confirmationRequired,
      String bodyJson) {}
}
