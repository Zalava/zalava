package org.zalava.capabilities.selfknowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.zalava.api.extensions.content.ContentExtractor;
import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries;
import org.zalava.capabilities.selfknowledge.application.port.in.SelfKnowledgeQueries.RuntimeState;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.application.port.in.AccountLifecycle;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.support.DocumentFixtureRuntime;
import org.zalava.support.SecureZalavaComponentTest;

@SecureZalavaComponentTest
@Import(SelfKnowledgeArtifactComponentTest.FixtureConfiguration.class)
class SelfKnowledgeArtifactComponentTest {
  @Autowired SelfKnowledgeQueries queries;
  @Autowired ActorExecutionContext actors;
  @Autowired AccountLifecycle accounts;
  @Autowired DocumentFixtureRuntime runtime;

  @Test
  void applicationBeanQueriesAHostLoadedBuiltArtifactAndItsCurrentState() {
    var account =
        accounts.create(
            "self-knowledge-" + UUID.randomUUID(), "TemporaryPassword-123", AccountRole.ADMIN);
    var actor = new Actor(account.id());
    String id = "module:zalava-external-module-fixture";
    try {
      var active = actors.call(actor, AccountRole.ADMIN, () -> queries.find(id)).orElseThrow();
      assertThat(active.runtimeState()).isEqualTo(RuntimeState.ACTIVE);
      assertThat(active.services())
          .anyMatch(service -> service.id().equals(ContentExtractor.CONTRACT.serviceId()));
      assertThat(active.toString()).doesNotContain("fixture-secret", "credentialRef");
      runtime.available = false;
      var unavailable = actors.call(actor, AccountRole.ADMIN, () -> queries.find(id)).orElseThrow();
      assertThat(unavailable.runtimeState()).isEqualTo(RuntimeState.UNKNOWN);
      assertThat(unavailable.services()).isEmpty();
    } finally {
      runtime.available = true;
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FixtureConfiguration {
    @Bean
    @Primary
    DocumentFixtureRuntime documentFixtureRuntime() throws Exception {
      return new DocumentFixtureRuntime();
    }
  }
}
