package org.zalava.capabilities.selfknowledge.application;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.operation.application.port.out.ProviderCatalog;
import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries.Operation;
import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries.Page;
import org.zalava.capabilities.selfknowledge.application.port.out.MemberToolVisibility;
import org.zalava.capabilities.selfknowledge.domain.CapabilityIdentity;
import org.zalava.capabilities.selfknowledge.domain.CapabilitySearch;
import org.zalava.capabilities.selfknowledge.domain.CapabilityText;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.modules.web.application.RegisteredWebPage;
import org.zalava.modules.web.application.port.in.ModulePageAccess;
import org.zalava.modules.web.application.port.in.WebExtensionRoutes;

/** Projects only the operations and pages the account can see. */
public final class ModuleVisibleEntries {
  private static final int MAX_RESULTS = CapabilitySearch.MAX_RESULTS;
  private final ProviderCatalog providers;
  private final MemberToolVisibility access;
  private final WebExtensionRoutes routes;
  private final ModulePageAccess pageAccess;

  public ModuleVisibleEntries(
      ProviderCatalog providers,
      MemberToolVisibility access,
      WebExtensionRoutes routes,
      ModulePageAccess pageAccess) {
    this.providers = Objects.requireNonNull(providers);
    this.access = Objects.requireNonNull(access);
    this.routes = Objects.requireNonNull(routes);
    this.pageAccess = Objects.requireNonNull(pageAccess);
  }

  Snapshot snapshot() {
    return new Snapshot(providers.providers(), routes.pages());
  }

  record Snapshot(List<ZalavaProvider> providers, List<RegisteredWebPage> pages) {}

  List<Operation> operations(Account account, String moduleId, List<ZalavaProvider> providers) {
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

  List<Page> pages(Account account, String moduleId, List<RegisteredWebPage> pages) {
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
