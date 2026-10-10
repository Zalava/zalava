package org.zalava.web.ui.navigation;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
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
  public NavigationModel navigation(Authentication authentication, CsrfToken csrf) {
    var model = navigation.forAuthentication(authentication);
    if (csrf == null) return model;
    return new NavigationModel(model.items(), csrf.getParameterName(), csrf.getToken());
  }
}
