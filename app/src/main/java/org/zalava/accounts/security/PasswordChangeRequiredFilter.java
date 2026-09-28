package org.zalava.accounts.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.zalava.accounts.application.port.in.AccountLifecycle;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class PasswordChangeRequiredFilter extends OncePerRequestFilter {
  private static final Set<String> ALLOWED = Set.of("/account/password", "/logout", "/login");
  private final AccountLifecycle accounts;

  public PasswordChangeRequiredFilter(AccountLifecycle accounts) {
    this.accounts = accounts;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null
        && auth.isAuthenticated()
        && !ALLOWED.contains(request.getRequestURI())
        && accounts
            .findByLoginName(auth.getName())
            .map(org.zalava.accounts.domain.Account::passwordChangeRequired)
            .orElse(false)) {
      response.sendRedirect("/account/password");
      return;
    }
    chain.doFilter(request, response);
  }
}
