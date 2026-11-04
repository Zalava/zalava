package org.zalava.capabilities.operation.adapter.out.jdbc;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation.Choice;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.ToolKey;
import org.zalava.capabilities.operation.application.port.out.ChatToolConfirmationStore;
import org.zalava.identity.accounts.domain.AccountRole;

public class JdbcChatToolConfirmationStore implements ChatToolConfirmationStore {
  private final JdbcClient jdbc;

  public JdbcChatToolConfirmationStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<ToolKey> savedTools() {
    return jdbc.sql(
            "SELECT module_id, provider_id, tool_name FROM zalava_chat_confirmation_role UNION SELECT module_id, provider_id, tool_name FROM zalava_chat_confirmation_user")
        .query(
            (rs, row) ->
                new ToolKey(
                    rs.getString("module_id"),
                    rs.getString("provider_id"),
                    rs.getString("tool_name")))
        .list();
  }

  @Override
  public Optional<Boolean> role(AccountRole role, ToolKey key) {
    return key(
            "SELECT require_confirmation FROM zalava_chat_confirmation_role WHERE role_name=:subject AND module_id=:module AND provider_id=:provider AND tool_name=:tool",
            role.name(),
            key)
        .query(Boolean.class)
        .optional();
  }

  @Override
  public Optional<Boolean> user(String accountId, ToolKey key) {
    return key(
            "SELECT require_confirmation FROM zalava_chat_confirmation_user WHERE account_id=:subject AND module_id=:module AND provider_id=:provider AND tool_name=:tool",
            UUID.fromString(accountId),
            key)
        .query(Boolean.class)
        .optional();
  }

  @Override
  @Transactional
  public void setRole(String admin, AccountRole role, ToolKey key, Choice choice, Instant at) {
    write(
        admin,
        "zalava_chat_confirmation_role",
        "role_name",
        role.name(),
        role.name(),
        null,
        key,
        choice,
        at);
  }

  @Override
  @Transactional
  public void setUser(String admin, String accountId, ToolKey key, Choice choice, Instant at) {
    UUID id = UUID.fromString(accountId);
    write(admin, "zalava_chat_confirmation_user", "account_id", id, null, id, key, choice, at);
  }

  private void write(
      String admin,
      String table,
      String column,
      Object subject,
      String role,
      UUID account,
      ToolKey key,
      Choice choice,
      Instant at) {
    if (choice == Choice.INHERIT) {
      key(
              "DELETE FROM "
                  + table
                  + " WHERE "
                  + column
                  + "=:subject AND module_id=:module AND provider_id=:provider AND tool_name=:tool",
              subject,
              key)
          .update();
    } else {
      key(
              "INSERT INTO "
                  + table
                  + "("
                  + column
                  + ",module_id,provider_id,tool_name,require_confirmation,changed_by,changed_at) VALUES(:subject,:module,:provider,:tool,:required,:admin,:at) ON CONFLICT("
                  + column
                  + ",module_id,provider_id,tool_name) DO UPDATE SET require_confirmation=EXCLUDED.require_confirmation,changed_by=EXCLUDED.changed_by,changed_at=EXCLUDED.changed_at",
              subject,
              key)
          .param("required", choice == Choice.REQUIRE)
          .param("admin", UUID.fromString(admin))
          .param("at", at.atOffset(ZoneOffset.UTC))
          .update();
    }
    jdbc.sql(
            "INSERT INTO zalava_chat_confirmation_audit(id,role_name,account_id,module_id,provider_id,tool_name,require_confirmation,changed_by,changed_at) VALUES(:id,:role,:account,:module,:provider,:tool,:required,:admin,:at)")
        .param("id", UUID.randomUUID())
        .param("role", role)
        .param("account", account)
        .param("module", key.moduleId())
        .param("provider", key.providerId())
        .param("tool", key.toolName())
        .param("required", choice == Choice.INHERIT ? null : choice == Choice.REQUIRE)
        .param("admin", UUID.fromString(admin))
        .param("at", at.atOffset(ZoneOffset.UTC))
        .update();
  }

  private JdbcClient.StatementSpec key(String sql, Object subject, ToolKey key) {
    return jdbc.sql(sql)
        .param("subject", subject)
        .param("module", key.moduleId())
        .param("provider", key.providerId())
        .param("tool", key.toolName());
  }
}
