package org.zalava.identity.accounts.adapter.out.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.zalava.identity.accounts.application.port.out.AccountPasswords;

public record SpringAccountPasswords(PasswordEncoder encoder) implements AccountPasswords {
  @Override
  public String encode(String password) {
    return encoder.encode(password);
  }

  @Override
  public boolean matches(String password, String encoded) {
    return encoder.matches(password, encoded);
  }
}
