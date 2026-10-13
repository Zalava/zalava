package org.zalava.web.ui.navigation;

import java.util.List;

/** Navigation entries and identity visible to the current account. */
public record NavigationModel(
    List<NavItem> items, String csrfParameter, String csrfToken, String accountName) {
  public NavigationModel(List<NavItem> items) {
    this(items, "", "", "");
  }

  public NavigationModel(List<NavItem> items, String csrfParameter, String csrfToken) {
    this(items, csrfParameter, csrfToken, "");
  }

  public NavigationModel {
    items = List.copyOf(items);
  }
}
