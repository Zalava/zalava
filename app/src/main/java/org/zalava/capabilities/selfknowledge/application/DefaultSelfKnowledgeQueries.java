package org.zalava.capabilities.selfknowledge.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.operation.application.port.out.ProviderCatalog;
import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries;
import org.zalava.capabilities.selfknowledge.application.port.out.MemberToolVisibility;
import org.zalava.capabilities.selfknowledge.application.port.out.ModuleStateQueries;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.modules.catalog.install.application.port.in.ModuleQueries;
import org.zalava.modules.runtime.application.port.in.RuntimeQueries;
import org.zalava.modules.web.application.port.in.ModulePageAccess;
import org.zalava.modules.web.application.port.in.WebExtensionRoutes;

/** Builds a fresh, bounded projection of existing host sources for the current actor. */
public final class DefaultSelfKnowledgeQueries implements SelfKnowledgeQueries {
  private static final int MAX_RESULTS = 50;
  private static final int MAX_QUERY = 200;
  private static final int MAX_TEXT = 500;

  private final ActorExecutionContext actors;
  private final AccountLifecycle accounts;
  private final RuntimeQueries runtime;
  private final ProviderCatalog providers;
  private final MemberToolVisibility access;
  private final WebExtensionRoutes routes;
  private final ModulePageAccess pageAccess;
  private final ModuleStateQueries moduleStates;
  private final ModuleQueries installedModules;

  public DefaultSelfKnowledgeQueries(
      ActorExecutionContext actors,
      AccountLifecycle accounts,
      RuntimeQueries runtime,
      ProviderCatalog providers,
      MemberToolVisibility access,
      WebExtensionRoutes routes,
      ModulePageAccess pageAccess,
      ModuleStateQueries moduleStates,
      ModuleQueries installedModules) {
    this.actors = Objects.requireNonNull(actors);
    this.accounts = Objects.requireNonNull(accounts);
    this.runtime = Objects.requireNonNull(runtime);
    this.providers = Objects.requireNonNull(providers);
    this.access = Objects.requireNonNull(access);
    this.routes = Objects.requireNonNull(routes);
    this.pageAccess = Objects.requireNonNull(pageAccess);
    this.moduleStates = Objects.requireNonNull(moduleStates);
    this.installedModules = Objects.requireNonNull(installedModules);
  }

  @Override
  public List<Capability> search(String query, int limit) {
    if (query == null || query.length() > MAX_QUERY || limit < 1 || limit > MAX_RESULTS) {
      throw new IllegalArgumentException("Invalid self-knowledge query or limit");
    }
    String term = query.strip().toLowerCase(Locale.ROOT);
    return visibleCapabilities().stream()
        .filter(value -> term.isEmpty() || searchable(value).contains(term))
        .sorted(Comparator.comparing(Capability::id))
        .limit(limit)
        .toList();
  }

  @Override
  public Optional<Capability> find(String id) {
    if (id == null || id.length() > 200) return Optional.empty();
    return visibleCapabilities().stream().filter(value -> value.id().equals(id)).findFirst();
  }

  private List<Capability> visibleCapabilities() {
    Account account =
        actors
            .currentPrincipal()
            .flatMap(principal -> accounts.findById(principal.actor().accountId()))
            .filter(Account::enabled)
            .orElseThrow(() -> new SecurityException("An enabled account is required"));
    Set<String> active =
        runtime.activeModules().stream()
            .map(module -> module.descriptor().moduleId())
            .collect(Collectors.toUnmodifiableSet());
    List<ZalavaProvider> currentProviders = providers.providers();
    var currentPages = routes.pages();
    List<Capability> result = new ArrayList<>();
    result.add(
        new Capability(
            "core:chat",
            "Chat",
            "Converse with the configured assistant.",
            "core",
            "",
            RuntimeState.CORE,
            Readiness.UNKNOWN,
            List.of(),
            List.of(),
            ""));
    runtime
        .modules()
        .forEach(
            module -> {
              var descriptor = module.descriptor();
              String moduleId = descriptor.moduleId();
              if (!validId(moduleId)) return;
              List<Operation> operations =
                  currentProviders.stream()
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
                      .sorted(
                          Comparator.comparing(Operation::providerId)
                              .thenComparing(Operation::name))
                      .limit(MAX_RESULTS)
                      .toList();
              List<Page> pages =
                  currentPages.stream()
                      .filter(page -> moduleId.equals(page.moduleId()))
                      .filter(page -> validPageId(moduleId))
                      .filter(page -> validPageId(page.pageId()))
                      .filter(
                          page ->
                              pageAccess.allowed(account.id().toString(), moduleId, page.pageId()))
                      .map(
                          page ->
                              new Page(
                                  page.pageId(),
                                  text(page.title()),
                                  text(page.description()),
                                  page.path()))
                      .sorted(Comparator.comparing(Page::id))
                      .limit(MAX_RESULTS)
                      .toList();
              if (account.role() != AccountRole.ADMIN && operations.isEmpty() && pages.isEmpty())
                return;
              result.add(
                  new Capability(
                      "module:" + moduleId,
                      text(descriptor.displayName()),
                      text(descriptor.description()),
                      "module",
                      text(descriptor.version()),
                      active.contains(moduleId)
                          ? RuntimeState.ACTIVE
                          : moduleStates.state(moduleId),
                      Readiness.UNKNOWN,
                      operations,
                      pages,
                      ""));
            });
    if (account.role() == AccountRole.ADMIN) {
      Set<String> loadedIds =
          runtime.modules().stream()
              .map(module -> module.descriptor().moduleId())
              .collect(Collectors.toSet());
      installedModules.enabledModules().stream()
          .filter(module -> validId(module.moduleId()) && !loadedIds.contains(module.moduleId()))
          .forEach(
              module ->
                  result.add(
                      new Capability(
                          "module:" + module.moduleId(),
                          module.moduleId(),
                          "Installed module is not loaded in this runtime.",
                          "module",
                          text(module.version()),
                          RuntimeState.NOT_LOADED,
                          Readiness.UNKNOWN,
                          List.of(),
                          List.of(),
                          "")));
    }
    return result;
  }

  private boolean permitted(Account account, ZalavaProvider provider, ZalavaToolDescriptor tool) {
    if (!validId(provider.descriptor().providerId()) || !validId(tool.name())) return false;
    if (account.role() == AccountRole.ADMIN) return true;
    return account.role() == AccountRole.MEMBER
        && access.allowed(account.id().toString(), provider, tool);
  }

  private static boolean validId(String value) {
    return value != null && value.matches("[a-zA-Z0-9][a-zA-Z0-9._-]{0,159}");
  }

  private static boolean validPageId(String value) {
    return value != null && value.length() <= 160 && value.matches("[a-z0-9][a-z0-9-]*");
  }

  private static String text(String value) {
    if (value == null) return "";
    String clean = value.replaceAll("[\\p{Cntrl}]", " ").strip();
    return clean.length() <= MAX_TEXT ? clean : clean.substring(0, MAX_TEXT);
  }

  private static String searchable(Capability capability) {
    String operations =
        capability.operations().stream()
            .map(operation -> operation.name() + " " + operation.description())
            .collect(Collectors.joining(" "));
    return (capability.id()
            + " "
            + capability.name()
            + " "
            + capability.description()
            + " "
            + operations)
        .toLowerCase(Locale.ROOT);
  }
}
