package org.zalava.assistant.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.zalava.assistant.agent.application.port.out.HostCallbackPolicy;
import org.zalava.identity.accounts.application.ActorExecutionContext;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.skills.application.port.in.SkillActivations;

class SkillContextEnrichmentTest {
  @Test
  void requiredConfirmationPreventsAutomaticSkillReads() {
    var actors = new ActorExecutionContext();
    var activations = mock(SkillActivations.class);
    HostCallbackPolicy policy =
        new HostCallbackPolicy() {
          @Override
          public Optional<String> beforeCall(String name, String input) {
            return Optional.empty();
          }

          @Override
          public boolean blockAutomaticContent(String name) {
            return name.equals("Skill");
          }
        };
    var enrichment =
        new SkillContextEnrichment(activations, actors, new ModelBoundary(1000, ""), true, policy);
    var result = actors.call(new Actor(AccountId.newId()), AccountRole.MEMBER, enrichment::select);
    assertThat(result).isEmpty();
    verifyNoInteractions(activations);
  }
}
