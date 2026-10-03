package org.zalava.web.ui.navigation;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Exposes the account-scoped navigation model to every MVC view. */
@ControllerAdvice
public final class NavigationModelAdvice {

  private final ZalavaNavigation navigation;

  public NavigationModelAdvice(ZalavaNavigation navigation) {
    this.navigation = navigation;
  }

  @ModelAttribute("navigation")
  public NavigationModel navigation(Authentication authentication) {
    return navigation.forAuthentication(authentication);
  }
}
