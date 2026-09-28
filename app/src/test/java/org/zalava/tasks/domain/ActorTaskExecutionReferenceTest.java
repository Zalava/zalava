package org.zalava.tasks.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;

class ActorTaskExecutionReferenceTest {
  @Test
  void roundTripsOnlyOpaqueOwnerAndTaskValues() {
    var reference =
        new ActorTaskExecutionReference(
            new Actor(AccountId.newId()), ActorTaskReference.newReference());

    String encoded = reference.encode();

    assertThat(ActorTaskExecutionReference.parse(encoded)).isEqualTo(reference);
    assertThat(encoded).doesNotContain("/", "\\", "tasks", "workspace");
  }

  @Test
  void rejectsMalformedOrTraversalLikeSchedulerTokens() {
    assertThatThrownBy(() -> ActorTaskExecutionReference.parse("../task"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ActorTaskExecutionReference.parse("a:b:c"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
