package org.zalava.identity.accounts.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.filter.OncePerRequestFilter;

public class AccountAuthorityRefreshFilter extends OncePerRequestFilter {
  private final ZalavaAccountUserDetailsService users;

  public AccountAuthorityRefreshFilter(ZalavaAccountUserDetailsService users) {
    this.users = users;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null
        && authentication.isAuthenticated()
        && authentication.getPrincipal() instanceof UserDetails current) {
      try {
        UserDetails latest = users.loadUserByUsername(current.getUsername());
        if (!latest.isEnabled()) {
          SecurityContextHolder.clearContext();
          var session = request.getSession(false);
          if (session != null) session.invalidate();
        } else {
          SecurityContextHolder.getContext()
              .setAuthentication(
                  new UsernamePasswordAuthenticationToken(latest, null, latest.getAuthorities()));
        }
      } catch (org.springframework.security.core.userdetails.UsernameNotFoundException ex) {
        SecurityContextHolder.clearContext();
      }
    }
    chain.doFilter(request, response);
  }
}
