package org.zalava.identity.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AccountConfigurationTest {

  private final AccountConfiguration configuration = new AccountConfiguration();

  @Test
  void retainsProductionDefaultBcryptStrength() {
    PasswordEncoder passwords =
        configuration.passwordEncoder(AccountConfiguration.DEFAULT_PASSWORD_ENCODER_STRENGTH);

    assertThat(passwords.encode("production-password")).startsWith("$2a$12$");
  }

  @Test
  void permitsTheTestBcryptStrengthWhileRetainingRealHashing() {
    PasswordEncoder passwords = configuration.passwordEncoder(4);
    String hash = passwords.encode("test-password");

    assertThat(hash).startsWith("$2a$04$");
    assertThat(passwords.matches("test-password", hash)).isTrue();
  }

  @Test
  void rejectsUnsupportedBcryptStrength() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> configuration.passwordEncoder(3))
        .withMessage("zalava.accounts.password-encoder-strength must be between 4 and 31");
  }
}
