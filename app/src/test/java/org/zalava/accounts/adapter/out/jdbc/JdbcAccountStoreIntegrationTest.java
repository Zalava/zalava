package org.zalava.accounts.adapter.out.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.zalava.accounts.application.port.out.AccountStore;
import org.zalava.accounts.domain.Account;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.persistence.OptimisticLockConflictException;

@SpringBootTest
class JdbcAccountStoreIntegrationTest {

  private static final Path DATABASE_PATH = createDatabasePath();

  @Autowired private AccountStore accounts;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    org.zalava.support.PostgreSqlTestDatabase.register(registry);
    registry.add("agent.workspace", () -> DATABASE_PATH.getParent().toUri().toString());
    registry.add("jobrunr.background-job-server.enabled", () -> "false");
    registry.add("jobrunr.dashboard.enabled", () -> "false");
    registry.add("sea.accounts.security-enabled", () -> "false");
    registry.add("sea.accounts.bootstrap-login", () -> "integration-admin");
    registry.add("sea.accounts.bootstrap-password", () -> "IntegrationPassword-123");
  }

  @Test
  void persistsAndReloadsAnAccountThroughTheFlywayManagedPostgreSqlSchema() {
    Instant createdAt = Instant.parse("2026-08-25T10:15:30Z");
    Account account =
        new Account(
            new AccountId(UUID.randomUUID()),
            "integration-member",
            "password-hash",
            true,
            AccountRole.MEMBER,
            true,
            createdAt,
            createdAt,
            0);

    accounts.create(account);

    assertThat(accounts.findById(account.id())).contains(account);
    assertThat(accounts.findByLoginName("integration-member")).contains(account);
    assertThat(accounts.findAll()).extracting(Account::loginName).contains("integration-member");
  }

  @Test
  void incrementsVersionAndRejectsAStaleAccountUpdate() {
    Instant now = Instant.parse("2026-08-25T10:15:30Z");
    Account account =
        new Account(
            new AccountId(UUID.randomUUID()),
            "versioned-member",
            "password-hash",
            true,
            AccountRole.MEMBER,
            true,
            now,
            now,
            0);
    accounts.create(account);

    Account saved = accounts.save(account);

    assertThat(saved.version()).isEqualTo(1);
    assertThat(accounts.findById(account.id())).contains(saved);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> accounts.save(account))
        .isInstanceOf(OptimisticLockConflictException.class);
  }

  @Test
  void enforcesTheUniqueLoginNameConstraint() {
    Instant now = Instant.parse("2026-08-25T10:15:30Z");
    Account first =
        new Account(
            new AccountId(UUID.randomUUID()),
            "unique-member",
            "password-hash",
            true,
            AccountRole.MEMBER,
            true,
            now,
            now,
            0);
    Account duplicate =
        new Account(
            new AccountId(UUID.randomUUID()),
            "unique-member",
            "password-hash",
            true,
            AccountRole.MEMBER,
            true,
            now,
            now,
            0);
    accounts.create(first);

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> accounts.create(duplicate))
        .isInstanceOf(DuplicateKeyException.class);
  }

  private static Path createDatabasePath() {
    try {
      Path directory = Files.createTempDirectory("jdbc-account-store-integration-");
      Files.writeString(directory.resolve("AGENT.md"), "Integration test agent prompt.");
      Files.writeString(directory.resolve("INFO.md"), "Integration test environment info.");
      return directory.resolve("sea");
    } catch (IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }
}
