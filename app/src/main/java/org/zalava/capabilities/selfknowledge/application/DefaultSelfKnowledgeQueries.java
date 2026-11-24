package org.zalava.capabilities.selfknowledge.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.zalava.api.ZalavaModule;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.discovery.application.port.in.ModuleSuggestions;
import org.zalava.capabilities.operation.application.port.out.ProviderCatalog;
import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries;
import org.zalava.capabilities.selfknowledge.application.port.out.ChatProviderSelection;
import org.zalava.capabilities.selfknowledge.application.port.out.CoreVersion;
import org.zalava.capabilities.selfknowledge.application.port.out.MemberToolVisibility;
import org.zalava.capabilities.selfknowledge.application.port.out.ModuleStateQueries;
import org.zalava.capabilities.selfknowledge.domain.CapabilityIdentity;
import org.zalava.capabilities.selfknowledge.domain.CapabilitySearch;
import org.zalava.capabilities.selfknowledge.domain.CapabilityText;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.modules.catalog.install.application.port.in.ModuleQueries;
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
      CoreVersion coreVersion) {
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
  }

  @Override
  public List<Capability> search(String query, int limit) {
    CapabilitySearch search = new CapabilitySearch(query, limit);
    return visibleCapabilities().stream()
        .filter(
            value ->
                search.matches(
                    value.id(), value.name(), value.description(), searchableOperations(value)))
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
    Set<String> active =
        runtime.activeModules().stream()
            .map(module -> module.descriptor().moduleId())
            .collect(Collectors.toUnmodifiableSet());
    var source = new ModuleSources(active, providers.providers(), routes.pages());
    List<Capability> result = new ArrayList<>();
    result.add(coreChat());
    modules.stream()
        .map(module -> projectModule(account, module, source))
        .flatMap(Optional::stream)
        .forEach(result::add);
    if (account.role() == AccountRole.ADMIN) {
      result.addAll(unloadedModules(modules));
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
        RuntimeState.CORE,
        chatProvider.selected() ? ProviderSelection.SELECTED : ProviderSelection.NOT_SELECTED,
        Readiness.UNKNOWN,
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
    if (account.role() != AccountRole.ADMIN && operations.isEmpty() && pages.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new Capability(
            identity.orElseThrow().value(),
            text(descriptor.displayName()),
            text(descriptor.description()),
            "module",
            text(descriptor.version()),
            source.active().contains(moduleId) ? RuntimeState.ACTIVE : moduleStates.state(moduleId),
            ProviderSelection.UNKNOWN,
            Readiness.UNKNOWN,
            operations,
            pages,
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

  private List<Capability> unloadedModules(List<ZalavaModule> modules) {
    Set<String> loadedIds =
        modules.stream().map(module -> module.descriptor().moduleId()).collect(Collectors.toSet());
    return installedModules.enabledModules().stream()
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
                    RuntimeState.NOT_LOADED,
                    ProviderSelection.UNKNOWN,
                    Readiness.UNKNOWN,
                    List.of(),
                    List.of(),
                    ""))
        .toList();
  }

  private record ModuleSources(
      Set<String> active, List<ZalavaProvider> providers, List<RegisteredWebPage> pages) {}

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

  private static String searchableOperations(Capability capability) {
    return capability.operations().stream()
        .map(operation -> operation.name() + " " + operation.description())
        .collect(Collectors.joining(" "));
  }
}
