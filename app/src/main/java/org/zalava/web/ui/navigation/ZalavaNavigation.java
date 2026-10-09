package org.zalava.web.ui.navigation;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.WebInvocationPrivilegeEvaluator;
import org.springframework.stereotype.Component;

/**
 * Builds the product navigation for an account. Visibility is decided by the real Spring Security
 * filter chain through {@link WebInvocationPrivilegeEvaluator}, so the menu can never link a page
 * the account is not authorized to open.
 */
@Component
public final class ZalavaNavigation {

  private static final List<NavItem> ITEMS =
      List.of(
          new NavItem("dashboard", "Dashboard", "/dashboard"),
          new NavItem("chat", "Chat", "/chat"),
          new NavItem("jobs", "Jobs", "/jobs"),
          new NavItem("knowledge", "Knowledge", "/knowledge"),
          new NavItem("memory", "Memory", "/memory"),
          new NavItem("monitoring", "Monitoring", "/monitoring"),
          new NavItem("apps", "Apps", "/apps"),
          new NavItem("modules", "Modules", "/modules"),
          new NavItem("settings", "Settings", "/settings"),
          new NavItem("advanced", "Advanced settings", "/zalava/control"));

  private final WebInvocationPrivilegeEvaluator privilegeEvaluator;

  public ZalavaNavigation(WebInvocationPrivilegeEvaluator privilegeEvaluator) {
    this.privilegeEvaluator = privilegeEvaluator;
  }

  public NavigationModel forAuthentication(Authentication authentication) {
    if (authentication == null) {
      return new NavigationModel(ITEMS);
    }
    return new NavigationModel(
        ITEMS.stream()
            .filter(item -> privilegeEvaluator.isAllowed("", item.href(), "GET", authentication))
            .toList());
  }
}
