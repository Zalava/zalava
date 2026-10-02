package org.zalava.assistant.channels.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.zalava.api.extensions.channels.ChannelCapabilities;
import org.zalava.api.extensions.channels.ChannelContentPrivacy;
import org.zalava.api.extensions.channels.ChannelDescriptor;
import org.zalava.api.extensions.channels.ChannelDestination;
import org.zalava.api.extensions.channels.ChannelEvent;
import org.zalava.api.extensions.channels.ChannelInput;
import org.zalava.api.extensions.channels.ChannelInteractionKind;
import org.zalava.api.extensions.channels.ChannelPrivacy;
import org.zalava.api.extensions.channels.ExternalIdentityReference;
import org.zalava.api.extensions.channels.IncomingInteraction;
import org.zalava.api.extensions.channels.ZalavaChannel;
import org.zalava.assistant.channels.runtime.application.DefaultChannelRuntime;
import org.zalava.assistant.channels.runtime.domain.ChannelIngressResult;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.identity.channels.application.port.in.ChannelIdentityLinks;
import org.zalava.identity.channels.domain.ChannelIdentityLink;
import org.zalava.identity.channels.domain.ChannelOperationScope;
import org.zalava.identity.channels.domain.ExternalChannelIdentity;

class DefaultChannelRuntimeTest {
  @Test
  void failedDispatchCanRetryAndCloseRemovesEveryTransport() {
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    DefaultChannelRuntime runtime =
        new DefaultChannelRuntime(
            links(ACTOR),
            ignored -> {
              if (calls.getAndIncrement() == 0) throw new IllegalStateException("Chat unavailable");
            });
    var channel = org.mockito.Mockito.spy(new FixtureChannel("fixture", fullCapabilities()));
    runtime.register(channel);
    var input = interaction("retry", ChannelInteractionKind.CONVERSATION, "fixture", "private");
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> runtime.receive(input))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Chat unavailable");
    assertThat(runtime.receive(input)).isInstanceOf(ChannelIngressResult.Accepted.class);
    assertThat(calls.get()).isEqualTo(2);
    runtime.close();
    org.mockito.Mockito.verify(channel).close();
    assertThat(runtime.receive(input))
        .isEqualTo(new ChannelIngressResult.Denied(ChannelIngressResult.Reason.UNKNOWN_CHANNEL));
    assertThat(runtime.deliver(new ChannelEvent.Text(input.destination(), "unavailable", "closed")))
        .isFalse();
  }

  private static final Actor ACTOR = new Actor(new AccountId(UUID.randomUUID()));

  @Test
  void resolvesBeforeDispatchDeduplicatesAndAssociatesAnActorOwnedConversation() {
    List<org.zalava.assistant.channels.runtime.domain.ResolvedChannelInteraction> dispatched =
        new ArrayList<>();
    DefaultChannelRuntime runtime = new DefaultChannelRuntime(links(ACTOR), dispatched::add);
    FixtureChannel channel = new FixtureChannel("fixture", fullCapabilities());
    runtime.register(channel);
    IncomingInteraction first =
        interaction("one", ChannelInteractionKind.CONVERSATION, "fixture", "destination");
    IncomingInteraction second =
        interaction("two", ChannelInteractionKind.CONVERSATION, "fixture", "destination");

    assertThat(runtime.receive(first)).isInstanceOf(ChannelIngressResult.Accepted.class);
    assertThat(runtime.receive(first))
        .isEqualTo(new ChannelIngressResult.Denied(ChannelIngressResult.Reason.DUPLICATE));
    assertThat(runtime.receive(second)).isInstanceOf(ChannelIngressResult.Accepted.class);
    assertThat(dispatched).hasSize(2);
    assertThat(dispatched.get(0).actor()).isEqualTo(ACTOR);
    assertThat(dispatched.get(0).conversation()).isEqualTo(dispatched.get(1).conversation());
    assertThat(channel.receiver).isNotNull();
  }

  @Test
  void deniesSpoofedUnknownAndInsufficientlyScopedInteractionsBeforeDispatch() {
    List<Object> dispatched = new ArrayList<>();
    DefaultChannelRuntime runtime =
        new DefaultChannelRuntime(links(null), ignored -> dispatched.add(ignored));
    runtime.register(new FixtureChannel("fixture", fullCapabilities()));

    assertThat(
            runtime.receive(
                interaction("one", ChannelInteractionKind.CONVERSATION, "fixture", "destination")))
        .isEqualTo(new ChannelIngressResult.Denied(ChannelIngressResult.Reason.IDENTITY_DENIED));
    assertThat(
            runtime.receive(
                interaction("two", ChannelInteractionKind.CONVERSATION, "spoofed", "destination")))
        .isEqualTo(new ChannelIngressResult.Denied(ChannelIngressResult.Reason.UNKNOWN_CHANNEL));
    assertThat(dispatched).isEmpty();
  }

  @Test
  void requiresApprovalCapabilityAndApprovalScopeBeforeDispatch() {
    List<Object> dispatched = new ArrayList<>();
    DefaultChannelRuntime noApproval =
        new DefaultChannelRuntime(links(ACTOR), ignored -> dispatched.add(ignored));
    noApproval.register(new FixtureChannel("fixture", ChannelCapabilities.textOnly()));
    assertThat(
            noApproval.receive(
                interaction(
                    "one", ChannelInteractionKind.APPROVAL_RESPONSE, "fixture", "destination")))
        .isEqualTo(
            new ChannelIngressResult.Denied(ChannelIngressResult.Reason.UNSUPPORTED_CAPABILITY));

    DefaultChannelRuntime deniedScope =
        new DefaultChannelRuntime(
            linksForOperation("chat:send"), ignored -> dispatched.add(ignored));
    deniedScope.register(new FixtureChannel("fixture", fullCapabilities()));
    assertThat(
            deniedScope.receive(
                interaction(
                    "two", ChannelInteractionKind.APPROVAL_RESPONSE, "fixture", "destination")))
        .isEqualTo(new ChannelIngressResult.Denied(ChannelIngressResult.Reason.IDENTITY_DENIED));
    assertThat(dispatched).isEmpty();
  }

  @Test
  void deliversOnlySupportedEventsAndNeverPrivateContentToSharedDestination() {
    DefaultChannelRuntime runtime = new DefaultChannelRuntime(links(ACTOR), ignored -> {});
    FixtureChannel channel = new FixtureChannel("fixture", fullCapabilities());
    runtime.register(channel);
    ChannelDestination shared = new ChannelDestination("fixture", "group", ChannelPrivacy.SHARED);
    ChannelDestination privateDestination =
        new ChannelDestination("fixture", "direct", ChannelPrivacy.PRIVATE);

    assertThat(
            runtime.deliver(
                new ChannelEvent.Text(shared, "secret", ChannelContentPrivacy.PRIVATE, "one")))
        .isFalse();
    assertThat(
            runtime.deliver(
                new ChannelEvent.Text(shared, "safe", ChannelContentPrivacy.SHAREABLE, "two")))
        .isTrue();
    assertThat(runtime.deliver(new ChannelEvent.Progress(privateDestination, "working", "three")))
        .isTrue();
    assertThat(channel.events).hasSize(2);
  }

  private static IncomingInteraction interaction(
      String id, ChannelInteractionKind kind, String identityChannel, String destination) {
    return new IncomingInteraction(
        id,
        new ExternalIdentityReference(identityChannel, "subject"),
        new ChannelDestination(identityChannel, destination, ChannelPrivacy.PRIVATE),
        kind,
        new ChannelInput.Text("hello"),
        "request-" + id);
  }

  private static ChannelCapabilities fullCapabilities() {
    return new ChannelCapabilities(true, true, false, false, false, false, true, false);
  }

  private static ChannelIdentityLinks links(Actor actor) {
    return linksForOperation(actor == null ? "" : "chat:send");
  }

  private static ChannelIdentityLinks linksForOperation(String allowedOperation) {
    return new ChannelIdentityLinks() {
      @Override
      public ChannelIdentityLink link(
          Actor owner, ExternalChannelIdentity identity, ChannelOperationScope scope) {
        throw new UnsupportedOperationException();
      }

      @Override
      public Optional<Actor> resolve(ExternalChannelIdentity identity, String operation) {
        return allowedOperation.equals(operation) ? Optional.of(ACTOR) : Optional.empty();
      }

      @Override
      public List<ChannelIdentityLink> links(Actor owner) {
        return List.of();
      }

      @Override
      public void revoke(Actor owner, UUID linkId) {
        throw new UnsupportedOperationException();
      }
    };
  }

  private static final class FixtureChannel implements ZalavaChannel {
    private final ChannelDescriptor descriptor;
    private org.zalava.api.extensions.channels.ChannelInteractionReceiver receiver;
    private final List<ChannelEvent> events = new ArrayList<>();

    private FixtureChannel(String id, ChannelCapabilities capabilities) {
      descriptor = new ChannelDescriptor(id, id, capabilities);
    }

    @Override
    public ChannelDescriptor descriptor() {
      return descriptor;
    }

    @Override
    public void bind(org.zalava.api.extensions.channels.ChannelInteractionReceiver receiver) {
      this.receiver = receiver;
    }

    @Override
    public void deliver(ChannelEvent event) {
      events.add(event);
    }
  }
}
