package org.zalava.accounts.adapter.out.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.zalava.accounts.application.port.out.AccountStore;
import org.zalava.accounts.domain.Account;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.persistence.OptimisticLockConflictException;

public final class JdbcAccountStore implements AccountStore {
  private static final String ACCOUNT_COLUMNS =
      "id, login_name, password_hash, enabled, role, password_change_required, created_at, updated_at, version";

  private final JdbcClient jdbc;

  public JdbcAccountStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<Account> findAll() {
    return jdbc.sql("select " + ACCOUNT_COLUMNS + " from sea_account order by login_name")
        .query(this::map)
        .list();
  }

  @Override
  public Optional<Account> findByLoginName(String loginName) {
    return jdbc.sql("select " + ACCOUNT_COLUMNS + " from sea_account where login_name = :login")
        .param("login", loginName)
        .query(this::map)
        .optional();
  }

  @Override
  public Optional<Account> findById(AccountId id) {
    return jdbc.sql("select " + ACCOUNT_COLUMNS + " from sea_account where id = :id")
        .param("id", id.value())
        .query(this::map)
        .optional();
  }

  @Override
  public long enabledAdministratorCount() {
    return jdbc.sql("select count(*) from sea_account where enabled = true and role = 'ADMIN'")
        .query(Long.class)
        .single();
  }

  @Override
  public Account create(Account account) {
    jdbc.sql(
            "insert into sea_account (id, login_name, password_hash, enabled, role, password_change_required, created_at, updated_at, version) values (:id, :login, :hash, :enabled, :role, :required, :created, :updated, :version)")
        .param("id", account.id().value())
        .param("login", account.loginName())
        .param("hash", account.passwordHash())
        .param("enabled", account.enabled())
        .param("role", account.role().name())
        .param("required", account.passwordChangeRequired())
        .param("created", Timestamp.from(account.createdAt()))
        .param("updated", Timestamp.from(account.updatedAt()))
        .param("version", account.version())
        .update();
    return account;
  }

  @Override
  public Account save(Account account) {
    int updated =
        jdbc.sql(
                "update sea_account set password_hash=:hash, enabled=:enabled, role=:role, password_change_required=:required, updated_at=:updated, version=version+1 where id=:id and version=:expectedVersion")
            .param("id", account.id().value())
            .param("hash", account.passwordHash())
            .param("enabled", account.enabled())
            .param("role", account.role().name())
            .param("required", account.passwordChangeRequired())
            .param("updated", Timestamp.from(account.updatedAt()))
            .param("expectedVersion", account.version())
            .update();
    if (updated != 1) throw new OptimisticLockConflictException("Account", account.id());
    return account.withVersion(account.version() + 1);
  }

  private Account map(ResultSet row, int index) throws SQLException {
    return new Account(
        new AccountId(row.getObject("id", java.util.UUID.class)),
        row.getString("login_name"),
        row.getString("password_hash"),
        row.getBoolean("enabled"),
        AccountRole.valueOf(row.getString("role")),
        row.getBoolean("password_change_required"),
        row.getTimestamp("created_at").toInstant(),
        row.getTimestamp("updated_at").toInstant(),
        row.getLong("version"));
  }
}
