package org.zalava.identity.accounts.security;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Account;

public class ZalavaAccountUserDetailsService implements UserDetailsService {
  private final AccountLifecycle accounts;

  public ZalavaAccountUserDetailsService(AccountLifecycle accounts) {
    this.accounts = accounts;
  }

  @Override
  public UserDetails loadUserByUsername(String loginName) throws UsernameNotFoundException {
    Account account =
        accounts
            .findByLoginName(loginName)
            .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
    return User.withUsername(account.loginName())
        .password(account.passwordHash())
        .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + account.role().name())))
        .disabled(!account.enabled())
        .build();
  }
}
