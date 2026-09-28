package org.zalava.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.zalava.accounts.application.port.in.AccountLifecycle;

@TestConfiguration(proxyBeanMethods = false)
public class AuthenticatedMockMvcTestConfiguration {
  @Bean
  @Primary
  MockMvc authenticatedMockMvc(
      WebApplicationContext context, ComponentTestAccounts componentTestAccounts) {
    return MockMvcBuilders.webAppContextSetup(context)
        .defaultRequest(
            get("/")
                .with(
                    componentTestAccounts.authenticatedAs(
                        componentTestAccounts.newActivated(
                            org.zalava.accounts.domain.AccountRole.ADMIN)))
                .with(csrf()))
        .apply(springSecurity())
        .build();
  }

  @Bean
  ComponentTestAccounts componentTestAccounts(AccountLifecycle accounts) {
    return new ComponentTestAccounts(accounts);
  }
}
