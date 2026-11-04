package org.zalava.identity.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.RequestDispatcher;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.support.SecureZalavaComponentTest;

@SecureZalavaComponentTest
class AccountManagementComponentTest {
  private static final String ADMIN = "account-management-regression-admin";
  private static final String PASSWORD = "ComponentPassword-123";
  @Autowired MockMvc mockMvc;
  @Autowired AccountLifecycle accounts;

  @BeforeEach
  void prepareAdministrator() {
    var admin =
        accounts
            .findByLoginName(ADMIN)
            .orElseGet(() -> accounts.create(ADMIN, PASSWORD, AccountRole.ADMIN));
    if (admin.passwordChangeRequired()) {
      accounts.changePassword(admin.id(), PASSWORD, "ChangedComponentPassword-123");
    }
  }

  @Test
  void renderedDisableAndEnableFormsPersistTheirIntendedAccountState() throws Exception {
    String login = "form-member-" + UUID.randomUUID();
    var member = accounts.create(login, PASSWORD, AccountRole.MEMBER);
    for (boolean expected : new boolean[] {false, true}) {
      String html = managementHtml();
      var forms =
          Pattern.compile(
                  "<form[^>]*action=\"/zalava/accounts/enabled\"[^>]*>(.*?)</form>", Pattern.DOTALL)
              .matcher(html);
      String submittedValue = null;
      while (forms.find()) {
        String form = forms.group(1);
        if (form.contains(member.id().value().toString())) {
          var input = Pattern.compile("<input[^>]*name=\"enabled\"[^>]*>").matcher(form);
          assertThat(input.find()).isTrue();
          var value = Pattern.compile("value=\"([^\"]*)\"").matcher(input.group());
          submittedValue = value.find() ? value.group(1) : "";
          break;
        }
      }
      assertThat(submittedValue).as("value submitted by the rendered browser form").isNotNull();
      mockMvc
          .perform(
              post("/zalava/accounts/enabled")
                  .with(user(ADMIN).roles("ADMIN"))
                  .with(csrf())
                  .param("accountId", member.id().value().toString())
                  .param("enabled", submittedValue))
          .andExpect(status().is3xxRedirection());
      assertThat(accounts.findById(member.id()).orElseThrow().enabled()).isEqualTo(expected);
    }
  }

  @Test
  void duplicateLoginExplainsTheFieldAndRetainsSafeInputsWithoutPassword() throws Exception {
    String login = "duplicate-member-" + UUID.randomUUID();
    accounts.create(login, PASSWORD, AccountRole.MEMBER);
    var result =
        mockMvc
            .perform(
                post("/zalava/accounts/create")
                    .with(user(ADMIN).roles("ADMIN"))
                    .with(csrf())
                    .param("loginName", login)
                    .param("temporaryPassword", PASSWORD)
                    .param("passwordConfirmation", PASSWORD)
                    .param("role", "MEMBER"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    String html = redirectedHtml(result);
    assertThat(html)
        .contains("Login name is already in use", "value=\"" + login + "\"")
        .doesNotContain(PASSWORD);
    assertThat(result.getFlashMap().values().toString()).doesNotContain(PASSWORD);
  }

  @Test
  void successfulCreationConfirmsTheAction() throws Exception {
    String login = "created-member-" + UUID.randomUUID();
    var result =
        mockMvc
            .perform(
                post("/zalava/accounts/create")
                    .with(user(ADMIN).roles("ADMIN"))
                    .with(csrf())
                    .param("loginName", login)
                    .param("temporaryPassword", PASSWORD)
                    .param("passwordConfirmation", PASSWORD)
                    .param("role", "MEMBER"))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    String html = redirectedHtml(result);
    assertThat(html).contains("Account created", "role=\"status\"");
  }

  @Test
  void accountManagementUsesSharedNavigationAndReadableStates() throws Exception {
    assertThat(managementHtml())
        .contains("/zalava-ui.css", "Zalava navigation", "Enabled", "Administrator")
        .doesNotContain("Return to Zalava Control", "<td>true</td>", "<td>ADMIN</td>");
  }

  @Test
  void memberCanDiscoverPersonalPasswordEntryWithoutAdministratorSettings() throws Exception {
    String login = "personal-member-" + UUID.randomUUID();
    var member = accounts.create(login, PASSWORD, AccountRole.MEMBER);
    accounts.changePassword(member.id(), PASSWORD, "ChangedComponentPassword-123");
    String html =
        mockMvc
            .perform(get("/memory").with(user(login).roles("MEMBER")))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(html)
        .contains("href=\"/account/password\"", "My account")
        .doesNotContain("href=\"/settings\"");
  }

  @Test
  void servletErrorsOfferSafeBrandedRecoveryWithoutExceptionDetails() throws Exception {
    for (int code : new int[] {400, 403, 404, 500}) {
      String html =
          mockMvc
              .perform(
                  get("/error")
                      .accept("text/html")
                      .with(
                          request -> {
                            request.setDispatcherType(DispatcherType.ERROR);
                            return request;
                          })
                      .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, code)
                      .requestAttr(RequestDispatcher.ERROR_MESSAGE, "private-source-secret")
                      .requestAttr(
                          RequestDispatcher.ERROR_EXCEPTION,
                          new IllegalStateException("private-source-secret")))
              .andExpect(status().is(code))
              .andReturn()
              .getResponse()
              .getContentAsString();
      assertThat(html)
          .contains("/zalava-ui.css", "Return to Chat")
          .doesNotContain("Whitelabel", "private-source-secret");
    }
  }

  private String redirectedHtml(MvcResult result) throws Exception {
    var request = get(result.getResponse().getRedirectedUrl()).with(user(ADMIN).roles("ADMIN"));
    if (!result.getFlashMap().isEmpty()) request.flashAttrs(result.getFlashMap());
    return mockMvc
        .perform(request)
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  private String managementHtml() throws Exception {
    return mockMvc
        .perform(get("/zalava/accounts").with(user(ADMIN).roles("ADMIN")))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }
}
