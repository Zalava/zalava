package org.zalava.identity.accounts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Allows read-only monitoring endpoints to be scraped while protecting management operations.
 *
 * <p>Only applies when the management server runs on its own port (the actuator endpoints then
 * exist there and not on the public application port). When both share a port this chain matches
 * nothing, so the application authorization is unchanged and metrics never become public.
 */
@Configuration
@ConditionalOnProperty(
    name = "zalava.accounts.security-enabled",
    havingValue = "true",
    matchIfMissing = true)
class ManagementSecurityConfiguration {

  @Bean
  @Order(0)
  SecurityFilterChain actuatorSecurity(
      HttpSecurity http,
      @Value("${management.server.port:}") String managementServerPort,
      @Value("${server.port:8080}") String serverPort)
      throws Exception {
    boolean separateManagementServer =
        !managementServerPort.isBlank()
            && (managementServerPort.equals("0") || !managementServerPort.equals(serverPort));
    http.securityMatcher(
            separateManagementServer ? EndpointRequest.toAnyEndpoint() : request -> false)
        .authorizeHttpRequests(
            authorize ->
                authorize
                    .requestMatchers(EndpointRequest.to("health", "info", "prometheus", "metrics"))
                    .permitAll()
                    .anyRequest()
                    .hasRole("ADMIN"))
        .csrf(csrf -> csrf.spa());
    return http.build();
  }
}
