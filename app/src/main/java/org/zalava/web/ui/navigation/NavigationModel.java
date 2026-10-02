package org.zalava.web.ui.navigation;

import java.util.List;

/** Navigation entries visible to the current account, in display order. */
public record NavigationModel(List<NavItem> items) {

  public NavigationModel {
    items = List.copyOf(items);
  }
}
