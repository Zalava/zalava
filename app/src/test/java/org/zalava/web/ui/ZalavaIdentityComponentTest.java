package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.support.AuthenticatedZalavaComponentTest;

@org.springframework.test.context.ActiveProfiles("test")
@AuthenticatedZalavaComponentTest
class ZalavaIdentityComponentTest {
  @Autowired MockMvc mvc;
  @Autowired JdbcClient jdbc;

  @Test
  void exposesZalavaRoutesAndAssetsWithoutLegacyAliases() throws Exception {
    for (String path :
        new String[] {
          "/api/zalava/modules", "/zalava/accounts", "/zalava/control",
          "/zalava-ui.css", "/zalava-control.css", "/zalava-chat/assets/zalava-chat.js"
        }) {
      mvc.perform(get(path)).andExpect(status().isOk());
    }
    // Deliberately invalid old entry points prove the fresh-only boundary.
    for (String path :
        new String[] {
          "/api/sea/modules", "/sea/accounts", "/sea/control",
          "/sea-ui.css", "/sea-control.css", "/sea-chat/assets/sea-chat.js"
        }) {
      mvc.perform(get(path)).andExpect(status().isNotFound());
    }
  }

  @Test
  void freshPostgresSchemaUsesOnlyCurrentIdentityTables() {
    var tables =
        jdbc.sql("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")
            .query(String.class)
            .list();
    assertThat(tables)
        .contains("zalava_account", "zalava_channel_identity_link", "zalava_channel_link_challenge")
        .doesNotContain("sea_account", "sea_channel_identity_link", "sea_channel_link_challenge");
  }
}
