package org.zalava.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.support.AuthenticatedSeaComponentTest;
import org.zalava.support.ComponentTestAccounts;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Proves the product navigation is one shared, authority-driven fragment: every page shows the same
 * shell, a role only links pages it can actually open, and the chat shell uses the same navigation
 * as the JTE pages.
 */
@AuthenticatedSeaComponentTest
class NavigationComponentTest {

  private static final List<String> ADMIN_LINKS =
      List.of("/dashboard", "/chat", "/jobs", "/knowledge", "/apps", "/modules", "/settings");
  private static final List<String> MEMBER_LINKS =
      List.of("/dashboard", "/chat", "/jobs", "/knowledge");
  private static final List<String> MEMBER_HIDDEN_LINKS = List.of("/apps", "/modules", "/settings");

  private static final Map<String, String> PAGES =
      Map.of(
          "/dashboard", "dashboard",
          "/chat", "chat",
          "/jobs", "jobs",
          "/knowledge", "knowledge",
          "/apps", "apps",
          "/modules", "modules",
          "/settings", "settings");

  @Autowired private MockMvc mockMvc;

  @Autowired private ComponentTestAccounts accounts;

  @Test
  void administratorSeesEveryProductPageOnEveryPage() throws Exception {
    for (Map.Entry<String, String> page : PAGES.entrySet()) {
      String body = bodyOf(page.getKey());
      for (String link : ADMIN_LINKS) {
        assertThat(body).as("admin menu on %s", page.getKey()).contains(href(link));
      }
      assertThat(body).as("active item on %s", page.getKey()).contains(activeMarkup(page.getKey()));
    }
  }

  @Test
  void memberSeesOnlyAuthorizedPagesOnEveryPage() throws Exception {
    var member = accounts.newActivated(AccountRole.MEMBER);
    for (String path : MEMBER_LINKS) {
      String body =
          mockMvc
              .perform(get(path).with(accounts.authenticatedAs(member)))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse()
              .getContentAsString();
      for (String link : MEMBER_LINKS) {
        assertThat(body).as("member menu on %s", path).contains(href(link));
      }
      for (String hidden : MEMBER_HIDDEN_LINKS) {
        assertThat(body).as("hidden %s on %s", hidden, path).doesNotContain(href(hidden));
      }
      assertThat(body).as("active item on %s", path).contains(activeMarkup(path));
    }
  }

  @Test
  void visibleLinksAreReachableForEachRole() throws Exception {
    for (String link : ADMIN_LINKS) {
      mockMvc.perform(get(link)).andExpect(status().isOk());
    }
    var member = accounts.newActivated(AccountRole.MEMBER);
    for (String link : MEMBER_LINKS) {
      mockMvc.perform(get(link).with(accounts.authenticatedAs(member))).andExpect(status().isOk());
    }
    for (String hidden : MEMBER_HIDDEN_LINKS) {
      mockMvc
          .perform(get(hidden).with(accounts.authenticatedAs(member)))
          .andExpect(status().isForbidden());
    }
  }

  @Test
  void appsAndChatUseTheSharedNavigation() throws Exception {
    String apps = bodyOf("/apps");
    assertThat(apps).contains(href("/knowledge"), href("/modules"), activeMarkup("/apps"));

    String chat = bodyOf("/chat");
    assertThat(chat)
        .contains(
            "id=\"root\"", "/sea-chat/assets/sea-chat.js", href("/jobs"), activeMarkup("/chat"));
  }

  private String bodyOf(String path) throws Exception {
    return mockMvc
        .perform(get(path))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private static String href(String path) {
    return "href=\"" + path + "\"";
  }

  private static String activeMarkup(String path) {
    return "class=\"navbar-item is-active\" aria-current=\"page\" " + href(path);
  }
}
