package org.zalava.conversation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;

class ActorConversationIdTest {
  @Test
  void roundTripsOnlyAnActorAndOpaqueConversationReference() {
    Actor actor = new Actor(AccountId.newId());
    ConversationReference reference = ConversationReference.newReference();

    assertThat(ActorConversationId.parse(new ActorConversationId(actor, reference).value()))
        .isEqualTo(new ActorConversationId(actor, reference));
  }

  @Test
  void rejectsMalformedAndClientChosenNonOpaqueValues() {
    assertThatThrownBy(() -> ActorConversationId.parse("member:../other"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ActorConversationId.parse("member:conversation:extra"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
