package org.zalava.identity.accounts.application.port.out;

public interface AccountPasswords {
  String encode(String password);

  boolean matches(String password, String encoded);
}
