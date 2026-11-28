package org.zalava.capabilities.selfknowledge.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.zalava.api.ZalavaModule;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaServiceDescriptor;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.operation.application.port.out.ProviderCatalog;
import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries.Capability;
import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries.ConfigurationState;
import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries.ManagedDependency;
import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries.ManagedServiceState;
import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries.Operation;
import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries.Page;
import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries.ProviderSelection;
import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries.Readiness;
import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries.RuntimeState;
import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries.Service;
import org.zalava.capabilities.selfknowledge.application.port.out.MemberToolVisibility;
import org.zalava.capabilities.selfknowledge.application.port.out.ModuleConfigurationStateQueries;
import org.zalava.capabilities.selfknowledge.application.port.out.ModuleStateQueries;
import org.zalava.capabilities.selfknowledge.domain.CapabilityCompatibility;
import org.zalava.capabilities.selfknowledge.domain.CapabilityIdentity;
import org.zalava.capabilities.selfknowledge.domain.CapabilitySearch;
import org.zalava.capabilities.selfknowledge.domain.CapabilityText;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.modules.catalog.install.application.port.in.ModuleQueries;
import org.zalava.modules.managedservices.application.port.in.ManagedServiceQueries;
import org.zalava.modules.runtime.application.port.in.RuntimeQueries;
import org.zalava.modules.web.application.RegisteredWebPage;
import org.zalava.modules.web.application.port.in.ModulePageAccess;
import org.zalava.modules.web.application.port.in.WebExtensionRoutes;

/** Projects the runtime's modules for a particular account. */
public final class ModuleCapabilityProjection {
  private static final int MAX_RESULTS = CapabilitySearch.MAX_RESULTS;
  private final RuntimeQueries runtime;
  private final ProviderCatalog providers;
  private final MemberToolVisibility access;
  private final WebExtensionRoutes routes;
  private final ModulePageAccess pageAccess;
  private final ModuleStateQueries moduleStates;
  private final ModuleQueries installedModules;
  private final ModuleConfigurationStateQueries moduleConfiguration;
  private final ManagedServiceQueries managedServices;

  public ModuleCapabilityProjection(
      RuntimeQueries runtime,
      ProviderCatalog providers,
      MemberToolVisibility access,
      WebExtensionRoutes routes,
      ModulePageAccess pageAccess,
      ModuleStateQueries moduleStates,
      ModuleQueries installedModules,
      ModuleConfigurationStateQueries moduleConfiguration,
      ManagedServiceQueries managedServices) {
    this.runtime = Objects.requireNonNull(runtime);
    this.providers = Objects.requireNonNull(providers);
    this.access = Objects.requireNonNull(access);
    this.routes = Objects.requireNonNull(routes);
    this.pageAccess = Objects.requireNonNull(pageAccess);
    this.moduleStates = Objects.requireNonNull(moduleStates);
    this.installedModules = Objects.requireNonNull(installedModules);
    this.moduleConfiguration = Objects.requireNonNull(moduleConfiguration);
    this.managedServices = Objects.requireNonNull(managedServices);
  }

  List<Capability> visibleTo(Account account) {
    List<ZalavaModule> modules = runtime.modules();
    List<ModuleQueries.EnabledModule> enabled = installedModules.enabledModules();
    Set<String> active =
        runtime.activeModules().stream()
            .map(module -> module.descriptor().moduleId())
            .collect(Collectors.toUnmodifiableSet());
    var source =
        new ModuleSources(
            active,
            providers.providers(),
            routes.pages(),
            account.role() == AccountRole.ADMIN ? runtime.loadedServiceDescriptors() : List.of(),
            enabled);
    List<Capability> result = new ArrayList<>();
    modules.stream()
        .map(module -> projectModule(account, module, source))
        .flatMap(Optional::stream)
        .forEach(result::add);
    if (account.role() == AccountRole.ADMIN) result.addAll(unloadedModules(modules, enabled));
    return result;
  }

  private Optional<Capability> projectModule(
      Account account, ZalavaModule module, ModuleSources source) {
    var descriptor = module.descriptor();
    String moduleId = descriptor.moduleId();
    var identity = CapabilityIdentity.module(moduleId);
    if (identity.isEmpty()) return Optional.empty();
    List<Operation> operations = visibleOperations(account, moduleId, source.providers());
    List<Page> pages = visiblePages(account, moduleId, source.pages());
    List<Service> services = visibleServices(moduleId, source.services());
    if (account.role() != AccountRole.ADMIN && operations.isEmpty() && pages.isEmpty()) {
      return Optional.empty();
    }
    var matched =
        source.enabled().stream()
            .filter(item -> moduleId.equals(item.moduleId()))
            .filter(
                item ->
                    CapabilityCompatibility.matchesVersion(descriptor.version(), item.version()))
            .findFirst();
    String compatibility = matched.map(item -> text(item.runtimeCompatibility())).orElse("");
    List<String> requirements =
        account.role() == AccountRole.ADMIN
            ? matched.map(this::requirements).orElseGet(List::of)
            : List.of();
    return Optional.of(
        new Capability(
            identity.orElseThrow().value(),
            text(descriptor.displayName()),
            text(descriptor.description()),
            "module",
            text(descriptor.version()),
            compatibility,
            requirements,
            source.active().contains(moduleId) ? RuntimeState.ACTIVE : moduleStates.state(moduleId),
            ProviderSelection.UNKNOWN,
            configurationState(module),
            Readiness.UNKNOWN,
            operations,
            pages,
            services,
            account.role() == AccountRole.ADMIN ? managedDependencies(moduleId) : List.of(),
            ""));
  }

  private List<Operation> visibleOperations(
      Account account, String moduleId, List<ZalavaProvider> providers) {
    return providers.stream()
        .filter(provider -> moduleId.equals(provider.descriptor().moduleId()))
        .flatMap(
            provider ->
                provider.listTools().stream()
                    .filter(tool -> permitted(account, provider, tool))
                    .map(
                        tool ->
                            new Operation(
                                provider.descriptor().providerId(),
                                tool.name(),
                                text(tool.description()),
                                tool.sideEffecting())))
        .sorted(Comparator.comparing(Operation::providerId).thenComparing(Operation::name))
        .limit(MAX_RESULTS)
        .toList();
  }

  private List<Page> visiblePages(Account account, String moduleId, List<RegisteredWebPage> pages) {
    if (!validPageId(moduleId)) return List.of();
    return pages.stream()
        .filter(page -> moduleId.equals(page.moduleId()))
        .filter(page -> validPageId(page.pageId()))
        .filter(page -> pageAccess.allowed(account.id().toString(), moduleId, page.pageId()))
        .map(
            page ->
                new Page(page.pageId(), text(page.title()), text(page.description()), page.path()))
        .sorted(Comparator.comparing(Page::id))
        .limit(MAX_RESULTS)
        .toList();
  }

  private List<Service> visibleServices(
      String moduleId, List<ZalavaServiceDescriptor> descriptors) {
    return descriptors.stream()
        .filter(service -> moduleId.equals(service.moduleId()))
        .filter(service -> CapabilityIdentity.validPart(service.serviceId()))
        .map(service -> new Service(service.serviceId(), text(service.contractVersion())))
        .sorted(Comparator.comparing(Service::id))
        .limit(MAX_RESULTS)
        .toList();
  }

  private List<ManagedDependency> managedDependencies(String moduleId) {
    return managedServices.forModule(moduleId).stream()
        .filter(service -> CapabilityIdentity.validPart(service.serviceId()))
        .map(
            service ->
                new ManagedDependency(service.serviceId(), managedState(service.observedState())))
        .sorted(Comparator.comparing(ManagedDependency::id))
        .limit(MAX_RESULTS)
        .toList();
  }

  private static ManagedServiceState managedState(String state) {
    if (state == null) return ManagedServiceState.UNKNOWN;
    return switch (state) {
      case "ABSENT" -> ManagedServiceState.ABSENT;
      case "STARTING" -> ManagedServiceState.STARTING;
      case "RUNNING" -> ManagedServiceState.RUNNING;
      case "STOPPED" -> ManagedServiceState.STOPPED;
      case "FAILED" -> ManagedServiceState.FAILED;
      default -> ManagedServiceState.UNKNOWN;
    };
  }

  private List<Capability> unloadedModules(
      List<ZalavaModule> modules, List<ModuleQueries.EnabledModule> enabled) {
    Set<String> loadedIds =
        modules.stream().map(module -> module.descriptor().moduleId()).collect(Collectors.toSet());
    return enabled.stream()
        .filter(
            module ->
                CapabilityIdentity.validPart(module.moduleId())
                    && !loadedIds.contains(module.moduleId()))
        .map(
            module ->
                new Capability(
                    CapabilityIdentity.module(module.moduleId()).orElseThrow().value(),
                    module.moduleId(),
                    "Installed module is not loaded in this runtime.",
                    "module",
                    text(module.version()),
                    text(module.runtimeCompatibility()),
                    requirements(module),
                    RuntimeState.NOT_LOADED,
                    ProviderSelection.UNKNOWN,
                    ConfigurationState.UNKNOWN,
                    Readiness.UNKNOWN,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    ""))
        .toList();
  }

  private record ModuleSources(
      Set<String> active,
      List<ZalavaProvider> providers,
      List<RegisteredWebPage> pages,
      List<ZalavaServiceDescriptor> services,
      List<ModuleQueries.EnabledModule> enabled) {}

  private List<String> requirements(ModuleQueries.EnabledModule module) {
    return module.declaredPermissions().stream()
        .map(ModuleCapabilityProjection::text)
        .filter(value -> !value.isBlank())
        .distinct()
        .sorted()
        .limit(MAX_RESULTS)
        .toList();
  }

  private ConfigurationState configurationState(ZalavaModule module) {
    var descriptor = module.configuration();
    if (descriptor == null) return ConfigurationState.UNKNOWN;
    Object properties = descriptor.jsonSchema().get("properties");
    if (!(properties instanceof Map<?, ?> map)) return ConfigurationState.UNKNOWN;
    return map.isEmpty()
        ? ConfigurationState.NOT_REQUIRED
        : moduleConfiguration.state(module.descriptor().moduleId());
  }

  private boolean permitted(Account account, ZalavaProvider provider, ZalavaToolDescriptor tool) {
    if (!CapabilityIdentity.validPart(provider.descriptor().providerId())
        || !CapabilityIdentity.validPart(tool.name())) return false;
    if (account.role() == AccountRole.ADMIN) return true;
    return account.role() == AccountRole.MEMBER
        && access.allowed(account.id().toString(), provider, tool);
  }

  private static boolean validPageId(String value) {
    return value != null && value.length() <= 160 && value.matches("[a-z0-9][a-z0-9-]*");
  }

  private static String text(String value) {
    return new CapabilityText(value).value();
  }
}
