package org.zalava.chat.api;

import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.zalava.accounts.application.port.in.AccountLifecycle;
import org.zalava.accounts.domain.AccountRole;

@TestConfiguration(proxyBeanMethods = false)
class BrowserChatAccountConfiguration {
  @Bean
  @Order(-100)
  ApplicationRunner browserChatAccount(AccountLifecycle accounts) {
    return arguments ->
        accounts
            .findByLoginName(BrowserChatAcceptanceTest.LOGIN)
            .orElseGet(
                () ->
                    accounts.create(
                        BrowserChatAcceptanceTest.LOGIN,
                        "BrowserTestPassword-123",
                        AccountRole.ADMIN));
  }
}
