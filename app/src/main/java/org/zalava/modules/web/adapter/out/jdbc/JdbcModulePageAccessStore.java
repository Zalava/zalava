package org.zalava.modules.web.adapter.out.jdbc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.zalava.modules.web.application.port.out.ModulePageAccessStore;

public class JdbcModulePageAccessStore implements ModulePageAccessStore {
  private final JdbcClient jdbc;

  public JdbcModulePageAccessStore(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<PageKey> savedPages() {
    return jdbc.sql(
            "SELECT module_id,page_id FROM zalava_page_access_default UNION SELECT module_id,page_id FROM zalava_page_access_user")
        .query((rs, n) -> new PageKey(rs.getString("module_id"), rs.getString("page_id")))
        .list();
  }

  @Override
  public boolean memberDefault(String module, String page) {
    return jdbc.sql(
            "SELECT allowed FROM zalava_page_access_default WHERE module_id=:module AND page_id=:page")
        .param("module", module)
        .param("page", page)
        .query(Boolean.class)
        .optional()
        .orElse(false);
  }

  @Override
  public Optional<Boolean> override(String account, String module, String page) {
    return jdbc.sql(
            "SELECT allowed FROM zalava_page_access_user WHERE account_id=:account AND module_id=:module AND page_id=:page")
        .param("account", UUID.fromString(account))
        .param("module", module)
        .param("page", page)
        .query(Boolean.class)
        .optional();
  }

  @Override
  @Transactional
  public void setDefault(String admin, String module, String page, boolean allowed) {
    jdbc.sql(
            "INSERT INTO zalava_page_access_default(module_id,page_id,allowed,changed_by) VALUES(:module,:page,:allowed,:admin) ON CONFLICT(module_id,page_id) DO UPDATE SET allowed=EXCLUDED.allowed,changed_by=EXCLUDED.changed_by,changed_at=CURRENT_TIMESTAMP")
        .param("module", module)
        .param("page", page)
        .param("allowed", allowed)
        .param("admin", UUID.fromString(admin))
        .update();
    audit(admin, null, module, page, allowed ? "ALLOW" : "DENY");
  }

  @Override
  @Transactional
  public void setOverride(
      String admin, String account, String module, String page, String decision) {
    var id = UUID.fromString(account);
    if (decision.equals("INHERIT"))
      jdbc.sql(
              "DELETE FROM zalava_page_access_user WHERE account_id=:account AND module_id=:module AND page_id=:page")
          .param("account", id)
          .param("module", module)
          .param("page", page)
          .update();
    else
      jdbc.sql(
              "INSERT INTO zalava_page_access_user(account_id,module_id,page_id,allowed,changed_by) VALUES(:account,:module,:page,:allowed,:admin) ON CONFLICT(account_id,module_id,page_id) DO UPDATE SET allowed=EXCLUDED.allowed,changed_by=EXCLUDED.changed_by,changed_at=CURRENT_TIMESTAMP")
          .param("account", id)
          .param("module", module)
          .param("page", page)
          .param("allowed", decision.equals("ALLOW"))
          .param("admin", UUID.fromString(admin))
          .update();
    audit(admin, id, module, page, decision);
  }

  private void audit(String admin, UUID account, String module, String page, String decision) {
    jdbc.sql(
            "INSERT INTO zalava_page_access_audit(id,account_id,module_id,page_id,decision,changed_by) VALUES(:id,:account,:module,:page,:decision,:admin)")
        .param("id", UUID.randomUUID())
        .param("account", account)
        .param("module", module)
        .param("page", page)
        .param("decision", decision)
        .param("admin", UUID.fromString(admin))
        .update();
  }
}
