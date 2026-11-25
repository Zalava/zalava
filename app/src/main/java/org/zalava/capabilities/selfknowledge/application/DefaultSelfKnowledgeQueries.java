package org.zalava.capabilities.selfknowledge.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.zalava.api.ZalavaModule;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaServiceDescriptor;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.discovery.application.port.in.ModuleSuggestions;
import org.zalava.capabilities.operation.application.port.out.ProviderCatalog;
import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries;
import org.zalava.capabilities.selfknowledge.application.port.out.ChatProviderSelection;
import org.zalava.capabilities.selfknowledge.application.port.out.CoreVersion;
import org.zalava.capabilities.selfknowledge.application.port.out.MemberToolVisibility;
import org.zalava.capabilities.selfknowledge.application.port.out.ModuleConfigurationStateQueries;
import org.zalava.capabilities.selfknowledge.application.port.out.ModuleStateQueries;
import org.zalava.capabilities.selfknowledge.domain.CapabilityCompatibility;
import org.zalava.capabilities.selfknowledge.domain.CapabilityIdentity;
import org.zalava.capabilities.selfknowledge.domain.CapabilitySearch;
import org.zalava.capabilities.selfknowledge.domain.CapabilityText;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.modules.catalog.install.application.port.in.ModuleQueries;
import org.zalava.modules.managedservices.application.port.in.ManagedServiceQueries;
import org.zalava.modules.runtime.application.port.in.RuntimeQueries;
import org.zalava.modules.web.application.RegisteredWebPage;
import org.zalava.modules.web.application.port.in.ModulePageAccess;
import org.zalava.modules.web.application.port.in.WebExtensionRoutes;

/** Builds a fresh, bounded projection of existing host sources for the current actor. */
public final class DefaultSelfKnowledgeQueries implements SelfKnowledgeQueries {
  private static final int MAX_RESULTS = CapabilitySearch.MAX_RESULTS;

  private final ActorExecutionContext actors;
  private final AccountLifecycle accounts;
  private final RuntimeQueries runtime;
  private final ProviderCatalog providers;
  private final MemberToolVisibility access;
  private final WebExtensionRoutes routes;
  private final ModulePageAccess pageAccess;
  private final ModuleStateQueries moduleStates;
  private final ModuleQueries installedModules;
  private final ModuleSuggestions suggestions;
  private final ChatProviderSelection chatProvider;
  private final CoreVersion coreVersion;
  private final ModuleConfigurationStateQueries moduleConfiguration;
  private final ManagedServiceQueries managedServices;

  public DefaultSelfKnowledgeQueries(
      ActorExecutionContext actors,
      AccountLifecycle accounts,
      RuntimeQueries runtime,
      ProviderCatalog providers,
      MemberToolVisibility access,
      WebExtensionRoutes routes,
      ModulePageAccess pageAccess,
      ModuleStateQueries moduleStates,
      ModuleQueries installedModules,
      ModuleSuggestions suggestions,
      ChatProviderSelection chatProvider,
      CoreVersion coreVersion,
      ModuleConfigurationStateQueries moduleConfiguration,
      ManagedServiceQueries managedServices) {
    this.actors = Objects.requireNonNull(actors);
    this.accounts = Objects.requireNonNull(accounts);
    this.runtime = Objects.requireNonNull(runtime);
    this.providers = Objects.requireNonNull(providers);
    this.access = Objects.requireNonNull(access);
    this.routes = Objects.requireNonNull(routes);
    this.pageAccess = Objects.requireNonNull(pageAccess);
    this.moduleStates = Objects.requireNonNull(moduleStates);
    this.installedModules = Objects.requireNonNull(installedModules);
    this.suggestions = Objects.requireNonNull(suggestions);
    this.chatProvider = Objects.requireNonNull(chatProvider);
    this.coreVersion = Objects.requireNonNull(coreVersion);
    this.moduleConfiguration = Objects.requireNonNull(moduleConfiguration);
    this.managedServices = Objects.requireNonNull(managedServices);
  }

  @Override
  public List<Capability> search(String query, int limit) {
    CapabilitySearch search = new CapabilitySearch(query, limit);
    return visibleCapabilities().stream()
        .filter(
            value ->
                search.matches(
                    value.id(), value.name(), value.description(), searchableDetails(value)))
        .sorted(Comparator.comparing(Capability::id))
        .limit(limit)
        .toList();
  }

  @Override
  public Optional<Capability> find(String id) {
    if (id == null || id.length() > 200) return Optional.empty();
    return visibleCapabilities().stream().filter(value -> value.id().equals(id)).findFirst();
  }

  @Override
  public Optional<Documentation> documentation(String reference) {
    currentAccount();
    if (!"core:chat".equals(reference)) return Optional.empty();
    return Optional.of(
        new Documentation(
            "core:chat",
            "Chat",
            "Open Chat to converse with the assistant. Select a supported model provider in Settings. "
                + "A selected provider does not establish valid credentials or connectivity. "
                + "Tools remain subject to access and approval policies."));
  }

  @Override
  public CatalogResult suggestCatalog(String query) {
    Account account = currentAccount();
    ModuleSuggestions.Outcome outcome = suggestions.suggest(query);
    if (outcome.status() != ModuleSuggestions.Status.MATCH) {
      return new CatalogResult(CatalogStatus.valueOf(outcome.status().name()), List.of());
    }
    boolean admin = account.role() == AccountRole.ADMIN;
    Set<String> present =
        admin
            ? Stream.concat(
                    runtime.modules().stream().map(module -> module.descriptor().moduleId()),
                    installedModules.enabledModules().stream()
                        .map(ModuleQueries.EnabledModule::moduleId))
                .collect(Collectors.toUnmodifiableSet())
            : Set.of();
    var matches =
        outcome.modules().stream()
            .filter(module -> module != null && CapabilityIdentity.validPart(module.moduleId()))
            .filter(module -> !present.contains(module.moduleId()))
            .map(
                module ->
                    new CatalogSuggestion(
                        CapabilityIdentity.module(module.moduleId()).orElseThrow().value(),
                        text(module.displayName()),
                        text(module.description()),
                        admin ? Installation.NOT_ENABLED_OR_LOADED : Installation.UNKNOWN))
            .distinct()
            .limit(5)
            .toList();
    return new CatalogResult(
        matches.isEmpty() ? CatalogStatus.NO_MATCH : CatalogStatus.MATCH, matches);
  }

  private List<Capability> visibleCapabilities() {
    Account account = currentAccount();
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
    result.add(coreChat());
    modules.stream()
        .map(module -> projectModule(account, module, source))
        .flatMap(Optional::stream)
        .forEach(result::add);
    if (account.role() == AccountRole.ADMIN) {
      result.addAll(unloadedModules(modules, enabled));
    }
    return result;
  }

  private Capability coreChat() {
    return new Capability(
        "core:chat",
        "Chat",
        "Converse with the configured assistant.",
        "core",
        text(coreVersion.version()),
        "",
        List.of(),
        RuntimeState.CORE,
        chatProvider.selected() ? ProviderSelection.SELECTED : ProviderSelection.NOT_SELECTED,
        ConfigurationState.NOT_APPLICABLE,
        Readiness.UNKNOWN,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        "core:chat");
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
        .map(DefaultSelfKnowledgeQueries::text)
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

  private Account currentAccount() {
    return actors
        .currentPrincipal()
        .flatMap(principal -> accounts.findById(principal.actor().accountId()))
        .filter(Account::enabled)
        .orElseThrow(() -> new SecurityException("An enabled account is required"));
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

  private static String searchableDetails(Capability capability) {
    Stream<String> operations =
        capability.operations().stream()
            .map(operation -> operation.name() + " " + operation.description());
    Stream<String> pages =
        capability.pages().stream().map(page -> page.title() + " " + page.description());
    Stream<String> services = capability.services().stream().map(Service::id);
    return Stream.concat(Stream.concat(operations, pages), services)
        .collect(Collectors.joining(" "));
  }
}
