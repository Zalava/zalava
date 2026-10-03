package org.zalava.platform.storage.private_state;

import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.Resource;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.Actor;

@Configuration
public class PrivateStateConfiguration {
  @Bean
  ActorScopedPaths actorScopedPaths(@Value("${agent.workspace:Unknown}") Resource workspace)
      throws IOException {
    return new ActorScopedPaths(workspace.getFilePath());
  }

  @Bean
  LegacyPrivateStateMigration legacyPrivateStateMigration(
      @Value("${agent.workspace:Unknown}") Resource workspace, ActorScopedPaths paths)
      throws IOException {
    return new LegacyPrivateStateMigration(workspace.getFilePath(), paths);
  }

  /**
   * An explicit operator opt-in performs a safe dry-run by default. Applying the migration is a
   * separate, intentional managed-runtime operation.
   */
  @Bean
  @DependsOn("bootstrapAccount")
  @ConditionalOnProperty(name = "zalava.private-state.migration.enabled", havingValue = "true")
  ApplicationRunner migrateLegacyPrivateState(
      LegacyPrivateStateMigration migration,
      AccountLifecycle accounts,
      @Value("${zalava.accounts.bootstrap-login:}") String bootstrapLogin,
      @Value("${zalava.private-state.migration.dry-run:true}") boolean dryRun) {
    return ignored -> {
      String login = bootstrapLogin.isBlank() ? "bootstrap" : bootstrapLogin;
      Actor bootstrapActor =
          accounts
              .findByLoginName(login)
              .map(account -> new Actor(account.id()))
              .orElseThrow(
                  () -> new IllegalStateException("Bootstrap administrator was not found"));
      migration.migrate(bootstrapActor, dryRun);
    };
  }
}
