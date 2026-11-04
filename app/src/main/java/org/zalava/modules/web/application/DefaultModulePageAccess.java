package org.zalava.modules.web.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.modules.web.application.port.in.ModulePageAccess;
import org.zalava.modules.web.application.port.in.WebExtensionRoutes;
import org.zalava.modules.web.application.port.out.ModulePageAccessStore;

public final class DefaultModulePageAccess implements ModulePageAccess {
  private final AccountLifecycle accounts;
  private final WebExtensionRoutes routes;
  private final ModulePageAccessStore store;

  public DefaultModulePageAccess(
      AccountLifecycle accounts, WebExtensionRoutes routes, ModulePageAccessStore store) {
    this.accounts = accounts;
    this.routes = routes;
    this.store = store;
  }

  @Override
  public boolean allowed(String accountId, String moduleId, String pageId) {
    try {
      Account actor = account(accountId);
      if (!actor.enabled() || !available(moduleId, pageId)) return false;
      if (actor.role() == AccountRole.ADMIN) return true;
      return store
          .override(accountId, moduleId, pageId)
          .orElseGet(() -> store.memberDefault(moduleId, pageId));
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  @Override
  public List<RegisteredWebPage> pages(String admin) {
    requireAdmin(admin);
    var pages = new ArrayList<>(routes.pages());
    store.savedPages().stream()
        .filter(
            key ->
                pages.stream()
                    .noneMatch(
                        p ->
                            p.moduleId().equals(key.moduleId()) && p.pageId().equals(key.pageId())))
        .forEach(
            key ->
                pages.add(
                    new RegisteredWebPage(
                        key.moduleId(),
                        key.moduleId(),
                        "unavailable",
                        key.pageId(),
                        key.pageId(),
                        "App unavailable. Saved access applies when it starts; you can revoke access here.",
                        "apps",
                        List.of())));
    return List.copyOf(pages);
  }

  @Override
  public boolean memberDefault(String admin, String moduleId, String pageId) {
    requireAdmin(admin);
    return store.memberDefault(moduleId, pageId);
  }

  @Override
  public String override(String admin, String accountId, String moduleId, String pageId) {
    requireAdmin(admin);
    requireMember(accountId);
    return store
        .override(accountId, moduleId, pageId)
        .map(v -> v ? "ALLOW" : "DENY")
        .orElse("INHERIT");
  }

  @Override
  public void setDefault(String admin, String moduleId, String pageId, boolean allowed) {
    requireAdmin(admin);
    validateKey(moduleId, pageId);
    if (allowed && !available(moduleId, pageId))
      throw new IllegalArgumentException("Page unavailable");
    store.setDefault(admin, moduleId, pageId, allowed);
  }

  @Override
  public void setOverride(
      String admin, String accountId, String moduleId, String pageId, String decision) {
    requireAdmin(admin);
    requireMember(accountId);
    validateKey(moduleId, pageId);
    if (!List.of("INHERIT", "ALLOW", "DENY").contains(decision)
        || (decision.equals("ALLOW") && !available(moduleId, pageId)))
      throw new IllegalArgumentException("Invalid page access");
    store.setOverride(admin, accountId, moduleId, pageId, decision);
  }

  private void validateKey(String moduleId, String pageId) {
    if (moduleId == null
        || pageId == null
        || moduleId.length() > 160
        || pageId.length() > 160
        || !moduleId.matches("[a-z0-9][a-z0-9-]*")
        || !pageId.matches("[a-z0-9][a-z0-9-]*"))
      throw new IllegalArgumentException("Invalid page identity");
  }

  private boolean available(String moduleId, String pageId) {
    return routes.pages().stream()
        .anyMatch(p -> p.moduleId().equals(moduleId) && p.pageId().equals(pageId));
  }

  private Account account(String id) {
    return accounts
        .findById(new AccountId(UUID.fromString(id)))
        .orElseThrow(() -> new IllegalArgumentException("Account unavailable"));
  }

  private void requireAdmin(String id) {
    Account a = account(id);
    if (!a.enabled() || a.role() != AccountRole.ADMIN)
      throw new SecurityException("Administrator required");
  }

  private void requireMember(String id) {
    if (account(id).role() != AccountRole.MEMBER)
      throw new IllegalArgumentException("Member required");
  }
}
