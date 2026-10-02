package org.zalava.identity.accounts;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.zalava.identity.accounts.adapter.out.jdbc.JdbcAccountStore;
import org.zalava.identity.accounts.application.AccountLifecycleService;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.application.port.out.AccountStore;
import org.zalava.identity.accounts.security.*;
import org.zalava.identity.channels.adapter.out.jdbc.JdbcChannelIdentityLinkStore;
import org.zalava.identity.channels.adapter.out.jdbc.JdbcChannelLinkChallengeStore;
import org.zalava.identity.channels.application.DefaultChannelIdentityLinks;
import org.zalava.identity.channels.application.DefaultChannelLinkChallenges;
import org.zalava.identity.channels.application.port.in.ChannelIdentityLinks;
import org.zalava.identity.channels.application.port.in.ChannelLinkChallenges;
import org.zalava.identity.channels.application.port.out.ChannelIdentityLinkStore;
import org.zalava.identity.channels.application.port.out.ChannelLinkChallengeStore;

@Configuration
public class AccountConfiguration {
  static final int DEFAULT_PASSWORD_ENCODER_STRENGTH = 12;
  private static final int MINIMUM_PASSWORD_ENCODER_STRENGTH = 4;
  private static final int MAXIMUM_PASSWORD_ENCODER_STRENGTH = 31;

  @Bean
  Clock accountClock() {
    return Clock.systemUTC();
  }

  @Bean
  PasswordEncoder passwordEncoder(
      @Value("${sea.accounts.password-encoder-strength:" + DEFAULT_PASSWORD_ENCODER_STRENGTH + "}")
          int strength) {
    if (strength < MINIMUM_PASSWORD_ENCODER_STRENGTH
        || strength > MAXIMUM_PASSWORD_ENCODER_STRENGTH) {
      throw new IllegalArgumentException(
          "sea.accounts.password-encoder-strength must be between %d and %d"
              .formatted(MINIMUM_PASSWORD_ENCODER_STRENGTH, MAXIMUM_PASSWORD_ENCODER_STRENGTH));
    }
    return new BCryptPasswordEncoder(strength);
  }

  @Bean
  AccountStore accountStore(JdbcClient jdbc) {
    return new JdbcAccountStore(jdbc);
  }

  @Bean
  ChannelIdentityLinkStore channelIdentityLinkStore(JdbcClient jdbc) {
    return new JdbcChannelIdentityLinkStore(jdbc);
  }

  @Bean
  ChannelLinkChallengeStore channelLinkChallengeStore(JdbcClient jdbc) {
    return new JdbcChannelLinkChallengeStore(jdbc);
  }

  @Bean
  AccountLifecycle accountLifecycle(AccountStore store, PasswordEncoder passwords, Clock clock) {
    return new AccountLifecycleService(store, passwords, clock);
  }

  @Bean
  ChannelIdentityLinks channelIdentityLinks(
      AccountLifecycle accounts, ChannelIdentityLinkStore links, Clock clock) {
    return new DefaultChannelIdentityLinks(accounts, links, clock);
  }

  @Bean
  ChannelLinkChallenges channelLinkChallenges(
      AccountLifecycle accounts,
      ChannelIdentityLinks links,
      ChannelLinkChallengeStore challenges,
      Clock clock) {
    return new DefaultChannelLinkChallenges(accounts, links, challenges, clock);
  }

  @Bean
  SeaAccountUserDetailsService accountUsers(AccountLifecycle accounts) {
    return new SeaAccountUserDetailsService(accounts);
  }

  @Bean
  ApplicationRunner bootstrapAccount(
      AccountLifecycle accounts,
      @Value("${sea.accounts.bootstrap-login:}") String login,
      @Value("${sea.accounts.bootstrap-password:}") String password) {
    return arguments -> {
      if (accounts.findByLoginName(login.isBlank() ? "bootstrap" : login).isEmpty()) {
        if (login.isBlank() || password.isBlank())
          throw new IllegalStateException(
              "sea.accounts.bootstrap-login and sea.accounts.bootstrap-password are required for the first administrator");
        accounts.bootstrap(login, password);
      }
    };
  }

  @Bean
  @ConditionalOnProperty(
      name = "sea.accounts.security-enabled",
      havingValue = "true",
      matchIfMissing = true)
  SecurityFilterChain accountSecurity(
      HttpSecurity http, SeaAccountUserDetailsService users, AccountLifecycle accounts)
      throws Exception {
    return http.authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/login", "/sea-control.css", "/css/**", "/actuator/health")
                    .permitAll()
                    .requestMatchers("/sea/control/**")
                    .hasRole("ADMIN")
                    .requestMatchers("/sea/accounts/**")
                    .hasRole("ADMIN")
                    .requestMatchers(
                        "/dashboard",
                        "/chat",
                        "/jobs",
                        "/jobs/**",
                        "/knowledge",
                        "/knowledge/**",
                        "/memory",
                        "/memory/**",
                        "/api/clarifications",
                        "/api/clarifications/**",
                        "/api/memory-proposals",
                        "/api/memory-proposals/**",
                        "/api/skills",
                        "/api/skills/**",
                        "/api/channel-links",
                        "/api/channel-links/**",
                        "/ws/chat",
                        "/ws/ui/chat",
                        "/sea-chat/**",
                        "/sea-ui.css")
                    .hasAnyRole("ADMIN", "MEMBER")
                    .requestMatchers("/account/password", "/logout")
                    .authenticated()
                    .anyRequest()
                    .hasRole("ADMIN"))
        .csrf(csrf -> csrf.spa())
        .formLogin(
            form ->
                form.loginPage("/login")
                    .successHandler(
                        (request, response, authentication) -> {
                          boolean admin =
                              authentication.getAuthorities().stream()
                                  .anyMatch(
                                      authority -> authority.getAuthority().equals("ROLE_ADMIN"));
                          response.sendRedirect(
                              request.getContextPath() + (admin ? "/sea/control" : "/dashboard"));
                        })
                    .permitAll())
        .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
        .userDetailsService(users)
        .addFilterAfter(
            new AccountAuthorityRefreshFilter(users), UsernamePasswordAuthenticationFilter.class)
        .addFilterAfter(
            new PasswordChangeRequiredFilter(accounts), AccountAuthorityRefreshFilter.class)
        .build();
  }

  @Bean
  @ConditionalOnProperty(name = "sea.accounts.security-enabled", havingValue = "false")
  SecurityFilterChain testSecurity(HttpSecurity http) throws Exception {
    return http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
        .csrf(csrf -> csrf.disable())
        .build();
  }
}
