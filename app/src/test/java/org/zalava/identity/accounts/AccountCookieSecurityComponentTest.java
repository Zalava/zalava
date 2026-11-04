package org.zalava.identity.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.zalava.support.SecureZalavaComponentTest;

// Use a separate full context: csrf() request postprocessors in other security tests replace
// the filter's token repository with a test repository that does not issue application cookies.
@SecureZalavaComponentTest
@TestPropertySource(properties = "zalava.test.context=account-cookie-security")
class AccountCookieSecurityComponentTest {
  @Autowired MockMvc mockMvc;

  @Test
  void issuesAnExplicitSameSiteSpaCookieOnHttpAndHttps() throws Exception {
    for (boolean secure : new boolean[] {false, true}) {
      var response =
          mockMvc
              .perform(get("/login").secure(secure))
              .andExpect(status().isOk())
              .andReturn()
              .getResponse();
      var cookie = response.getCookie("XSRF-TOKEN");
      assertThat(cookie).isNotNull();
      assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
      assertThat(cookie.isHttpOnly()).isFalse();
      assertThat(cookie.getSecure()).isEqualTo(secure);
    }
  }
}
