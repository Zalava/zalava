package org.zalava.web.ui.navigation;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.WebInvocationPrivilegeEvaluator;
import org.springframework.stereotype.Component;
import org.zalava.modules.catalog.install.application.port.in.ModuleQueries;

/**
 * Builds the product navigation for an account. Visibility is decided by the real Spring Security
 * filter chain through {@link WebInvocationPrivilegeEvaluator}, so the menu can never link a page
 * the account is not authorized to open.
 */
@Component
public final class ZalavaNavigation {

  private static final List<NavItem> ITEMS =
      List.of(
          new NavItem("chat", "Chat", "/chat"),
          new NavItem("dashboard", "Dashboard", "/dashboard"),
          new NavItem("jobs", "Tasks", "/jobs"),
          new NavItem("knowledge", "Knowledge", "/knowledge"),
          new NavItem("memory", "Memory", "/memory"),
          new NavItem("monitoring", "Monitoring", "/monitoring"),
          new NavItem("apps", "Apps", "/apps"),
          new NavItem("modules", "Modules", "/modules"),
          new NavItem("settings", "Settings", "/settings"),
          new NavItem("advanced", "Advanced settings", "/zalava/control"),
          new NavItem("account", "My account", "/account/password"));

  private final WebInvocationPrivilegeEvaluator privilegeEvaluator;

  private final ModuleQueries modules;

  public ZalavaNavigation(
      WebInvocationPrivilegeEvaluator privilegeEvaluator, ModuleQueries modules) {
    this.privilegeEvaluator = privilegeEvaluator;
    this.modules = modules;
  }

  public NavigationModel forAuthentication(Authentication authentication) {
    if (authentication == null) {
      return new NavigationModel(ITEMS);
    }
    return new NavigationModel(
        ITEMS.stream()
            .filter(
                item ->
                    !item.id().equals("jobs")
                        || modules.enabledModules().stream()
                            .anyMatch(module -> module.moduleId().equals("tasks")))
            .filter(item -> privilegeEvaluator.isAllowed("", item.href(), "GET", authentication))
            .toList(),
        "",
        "",
        authentication.getName());
  }
}
