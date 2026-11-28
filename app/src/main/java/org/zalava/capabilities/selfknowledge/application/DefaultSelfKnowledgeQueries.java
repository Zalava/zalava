package org.zalava.capabilities.selfknowledge.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.zalava.capabilities.discovery.application.port.in.ModuleSuggestions;
import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries;
import org.zalava.capabilities.selfknowledge.application.port.out.ChatProviderSelection;
import org.zalava.capabilities.selfknowledge.application.port.out.CoreVersion;
import org.zalava.capabilities.selfknowledge.domain.CapabilityIdentity;
import org.zalava.capabilities.selfknowledge.domain.CapabilitySearch;
import org.zalava.capabilities.selfknowledge.domain.CapabilityText;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.modules.catalog.install.application.port.in.ModuleQueries;
import org.zalava.modules.runtime.application.port.in.RuntimeQueries;

/** Builds a fresh, bounded projection of existing host sources for the current actor. */
public final class DefaultSelfKnowledgeQueries implements SelfKnowledgeQueries {
  private static final int MAX_RESULTS = CapabilitySearch.MAX_RESULTS;

  private final ActorExecutionContext actors;
  private final AccountLifecycle accounts;
  private final RuntimeQueries runtime;
  private final ModuleQueries installedModules;
  private final ModuleSuggestions suggestions;
  private final ChatProviderSelection chatProvider;
  private final CoreVersion coreVersion;
  private final ModuleCapabilityProjection modules;

  public DefaultSelfKnowledgeQueries(
      ActorExecutionContext actors,
      AccountLifecycle accounts,
      RuntimeQueries runtime,
      ModuleQueries installedModules,
      ModuleSuggestions suggestions,
      ChatProviderSelection chatProvider,
      CoreVersion coreVersion,
      ModuleCapabilityProjection modules) {
    this.actors = Objects.requireNonNull(actors);
    this.accounts = Objects.requireNonNull(accounts);
    this.runtime = Objects.requireNonNull(runtime);
    this.installedModules = Objects.requireNonNull(installedModules);
    this.suggestions = Objects.requireNonNull(suggestions);
    this.chatProvider = Objects.requireNonNull(chatProvider);
    this.coreVersion = Objects.requireNonNull(coreVersion);
    this.modules = Objects.requireNonNull(modules);
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
    List<Capability> result = new ArrayList<>();
    result.add(coreChat());
    result.addAll(modules.visibleTo(account));
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

  private Account currentAccount() {
    return actors
        .currentPrincipal()
        .flatMap(principal -> accounts.findById(principal.actor().accountId()))
        .filter(Account::enabled)
        .orElseThrow(() -> new SecurityException("An enabled account is required"));
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
