package org.zalava.capabilities.operation.adapter.out.jdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.Decision;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.ToolKey;
import org.zalava.capabilities.operation.application.port.out.MemberToolAccessStore;

public class JdbcMemberToolAccessStore implements MemberToolAccessStore {
  private final JdbcClient jdbc;

  public JdbcMemberToolAccessStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean allowed(String memberId, ToolKey key) {
    return keyQuery(
            "SELECT allowed FROM zalava_member_tool_access WHERE member_id=:member AND module_id=:module AND provider_id=:provider AND tool_name=:tool",
            memberId,
            key)
        .query(Boolean.class)
        .optional()
        .orElse(false);
  }

  @Override
  public List<Decision> decisions(String memberId) {
    return jdbc.sql(
            "SELECT * FROM zalava_member_tool_access WHERE member_id=:member ORDER BY module_id, provider_id, tool_name")
        .param("member", UUID.fromString(memberId))
        .query(JdbcMemberToolAccessStore::decision)
        .list();
  }

  @Override
  @Transactional
  public void decide(String admin, String member, ToolKey key, boolean allowed, Instant at) {
    // Serialize decisions for this account, including first grant, without vendor-specific upserts.
    jdbc.sql("SELECT id FROM zalava_account WHERE id=:member FOR UPDATE")
        .param("member", UUID.fromString(member))
        .query(UUID.class)
        .single();
    int changed =
        keyQuery(
                "UPDATE zalava_member_tool_access SET allowed=:allowed, changed_by=:admin, changed_at=:at WHERE member_id=:member AND module_id=:module AND provider_id=:provider AND tool_name=:tool",
                member,
                key)
            .param("allowed", allowed)
            .param("admin", UUID.fromString(admin))
            .param("at", at.atOffset(ZoneOffset.UTC))
            .update();
    if (changed == 0)
      keyQuery(
              "INSERT INTO zalava_member_tool_access (member_id,module_id,provider_id,tool_name,allowed,changed_by,changed_at) VALUES (:member,:module,:provider,:tool,:allowed,:admin,:at)",
              member,
              key)
          .param("allowed", allowed)
          .param("admin", UUID.fromString(admin))
          .param("at", at.atOffset(ZoneOffset.UTC))
          .update();
    keyQuery(
            "INSERT INTO zalava_member_tool_access_audit (id,member_id,module_id,provider_id,tool_name,allowed,changed_by,changed_at) VALUES (:id,:member,:module,:provider,:tool,:allowed,:admin,:at)",
            member,
            key)
        .param("id", UUID.randomUUID())
        .param("allowed", allowed)
        .param("admin", UUID.fromString(admin))
        .param("at", at.atOffset(ZoneOffset.UTC))
        .update();
  }

  @Override
  public boolean defaultAllowed(ToolKey key) {
    return roleQuery(
            "SELECT allowed FROM zalava_tool_role_access WHERE module_id=:module AND provider_id=:provider AND tool_name=:tool",
            key)
        .query(Boolean.class)
        .optional()
        .orElse(false);
  }

  @Override
  public List<Decision> defaults() {
    return jdbc.sql(
            "SELECT * FROM zalava_tool_role_access ORDER BY module_id, provider_id, tool_name")
        .query(JdbcMemberToolAccessStore::decision)
        .list();
  }

  @Override
  @Transactional
  public void defaultAccess(String admin, ToolKey key, boolean allowed, Instant at) {
    roleQuery(
            "INSERT INTO zalava_tool_role_access(module_id,provider_id,tool_name,allowed,changed_by,changed_at) VALUES(:module,:provider,:tool,:allowed,:admin,:at) ON CONFLICT(module_id,provider_id,tool_name) DO UPDATE SET allowed=EXCLUDED.allowed,changed_by=EXCLUDED.changed_by,changed_at=EXCLUDED.changed_at",
            key)
        .param("allowed", allowed)
        .param("admin", UUID.fromString(admin))
        .param("at", at.atOffset(ZoneOffset.UTC))
        .update();
    roleQuery(
            "INSERT INTO zalava_tool_role_access_audit(id,module_id,provider_id,tool_name,allowed,changed_by,changed_at) VALUES(:id,:module,:provider,:tool,:allowed,:admin,:at)",
            key)
        .param("id", UUID.randomUUID())
        .param("allowed", allowed)
        .param("admin", UUID.fromString(admin))
        .param("at", at.atOffset(ZoneOffset.UTC))
        .update();
  }

  @Override
  @Transactional
  public void inherit(String admin, String member, ToolKey key, Instant at) {
    jdbc.sql("SELECT id FROM zalava_account WHERE id=:member FOR UPDATE")
        .param("member", UUID.fromString(member))
        .query(UUID.class)
        .single();
    keyQuery(
            "DELETE FROM zalava_member_tool_access WHERE member_id=:member AND module_id=:module AND provider_id=:provider AND tool_name=:tool",
            member,
            key)
        .update();
    keyQuery(
            "INSERT INTO zalava_member_tool_access_audit(id,member_id,module_id,provider_id,tool_name,allowed,changed_by,changed_at) VALUES(:id,:member,:module,:provider,:tool,NULL,:admin,:at)",
            member,
            key)
        .param("id", UUID.randomUUID())
        .param("admin", UUID.fromString(admin))
        .param("at", at.atOffset(ZoneOffset.UTC))
        .update();
  }

  private JdbcClient.StatementSpec roleQuery(String sql, ToolKey key) {
    return jdbc.sql(sql)
        .param("module", key.moduleId())
        .param("provider", key.providerId())
        .param("tool", key.toolName());
  }

  private JdbcClient.StatementSpec keyQuery(String sql, String member, ToolKey key) {
    return jdbc.sql(sql)
        .param("member", UUID.fromString(member))
        .param("module", key.moduleId())
        .param("provider", key.providerId())
        .param("tool", key.toolName());
  }

  private static Decision decision(ResultSet rs, int row) throws SQLException {
    return new Decision(
        new ToolKey(
            rs.getString("module_id"), rs.getString("provider_id"), rs.getString("tool_name")),
        rs.getBoolean("allowed"),
        rs.getString("changed_by"),
        rs.getObject("changed_at", OffsetDateTime.class).toInstant());
  }
}
