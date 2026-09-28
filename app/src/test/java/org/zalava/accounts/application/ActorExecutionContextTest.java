package org.zalava.accounts.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.accounts.domain.Actor;

/** Covers context propagation, nested restore, and clearing behavior of the trusted context. */
class ActorExecutionContextTest {

  private final ActorExecutionContext context = new ActorExecutionContext();
  private final Actor actor = new Actor(new AccountId(java.util.UUID.randomUUID()));

  @Test
  void exposesPrincipalOnlyInsideScope() {
    assertThat(context.currentPrincipal()).isEmpty();

    String result =
        context.call(
            actor,
            AccountRole.ADMIN,
            () -> {
              assertThat(context.currentPrincipal())
                  .hasValueSatisfying(
                      principal -> {
                        assertThat(principal.actor()).isEqualTo(actor);
                        assertThat(principal.role()).isEqualTo(AccountRole.ADMIN);
                      });
              return "done";
            });

    assertThat(result).isEqualTo("done");
    assertThat(context.currentPrincipal()).isEmpty();
  }

  @Test
  void restoresPreviousPrincipalAfterNestedScope() {
    Actor other = new Actor(new AccountId(java.util.UUID.randomUUID()));

    context.call(
        actor,
        AccountRole.ADMIN,
        () -> {
          context.call(
              other,
              AccountRole.MEMBER,
              () -> {
                assertThat(context.currentPrincipal())
                    .get()
                    .satisfies(
                        principal -> {
                          assertThat(principal.actor()).isEqualTo(other);
                          assertThat(principal.role()).isEqualTo(AccountRole.MEMBER);
                        });
                return null;
              });

          assertThat(context.currentPrincipal())
              .get()
              .satisfies(
                  principal -> {
                    assertThat(principal.actor()).isEqualTo(actor);
                    assertThat(principal.role()).isEqualTo(AccountRole.ADMIN);
                  });
          return null;
        });

    assertThat(context.currentPrincipal()).isEmpty();
  }

  @Test
  void restoresPrincipalWhenOperationThrows() {
    AtomicReference<String> observed = new AtomicReference<>();

    try {
      context.call(
          actor,
          AccountRole.MEMBER,
          () -> {
            observed.set(
                context
                    .currentPrincipal()
                    .map(p -> p.actor().accountId().toString())
                    .orElse("none"));
            throw new IllegalStateException("boom");
          });
    } catch (IllegalStateException expected) {
      // ignored
    }

    assertThat(observed.get()).isEqualTo(actor.accountId().toString());
    assertThat(context.currentPrincipal()).isEmpty();
  }

  @Test
  void currentPrincipalReturnsEmptyOutsideScope() {
    assertThat(context.currentPrincipal()).isEqualTo(Optional.empty());
  }
}
