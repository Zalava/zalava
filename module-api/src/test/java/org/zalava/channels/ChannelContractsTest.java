package org.zalava.channels;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.zalava.FactorySecretAccess;
import org.zalava.ZalavaModule;

class ChannelContractsTest {
  private static final ChannelDestination PRIVATE_DESTINATION =
      new ChannelDestination("telegram", "opaque-delivery-handle", ChannelPrivacy.PRIVATE);

  @Test
  void declaresCapabilitiesAndAddsChannelsWithoutBreakingExistingModules() {
    ChannelCapabilities capabilities = ChannelCapabilities.textOnly();
    ChannelDescriptor descriptor = new ChannelDescriptor("telegram", "Telegram", capabilities);

    assertThat(capabilities.text()).isTrue();
    assertThat(capabilities.proactiveNotifications()).isFalse();
    assertThat(descriptor.channelId()).isEqualTo("telegram");
    assertThat(new FixtureModule().channels()).isEmpty();
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ChannelDescriptor(" ", "Telegram", capabilities));
  }

  @Test
  void scopesTransportConfigurationAndPreservesExistingChannelImplementations() {
    Map<String, Object> values = new HashMap<>(Map.of("tokenRef", "telegram-token"));
    ChannelTransportContext context =
        new ChannelTransportContext(
            values, reference -> java.util.Optional.of(reference.toCharArray()));
    values.clear();
    assertThat(context.configuration()).containsEntry("tokenRef", "telegram-token");
    assertThat(context.secrets().resolve("telegram-token"))
        .hasValueSatisfying(
            value ->
                assertThat(value)
                    .containsExactly(
                        't', 'e', 'l', 'e', 'g', 'r', 'a', 'm', '-', 't', 'o', 'k', 'e', 'n'));
    assertThat(ChannelTransportContext.empty().configuration()).isEmpty();
    new RecordingChannel().start(new ChannelTransportContext(Map.of(), FactorySecretAccess.none()));
  }

  @Test
  void modelsTrustedIdentityAndOpaqueDestinationWithoutTransportSpecificAuthority() {
    ExternalIdentityReference identity =
        new ExternalIdentityReference("telegram", "stable-subject");
    ChannelDestination shared =
        new ChannelDestination("telegram", "opaque-group-handle", ChannelPrivacy.SHARED);

    assertThat(identity.subject()).isEqualTo("stable-subject");
    assertThat(shared.privacy()).isEqualTo(ChannelPrivacy.SHARED);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ExternalIdentityReference("telegram", " "));
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ChannelDestination("telegram", " ", ChannelPrivacy.PRIVATE));
  }

  @Test
  void requiresDeduplicableInboundInteractionWithOneChannelIdentityAndDestination() {
    IncomingInteraction interaction =
        new IncomingInteraction(
            "update-1",
            new ExternalIdentityReference("telegram", "subject"),
            PRIVATE_DESTINATION,
            ChannelInteractionKind.CONVERSATION,
            new ChannelInput.Text(" hello "),
            "request-1");

    assertThat(((ChannelInput.Text) interaction.input()).value()).isEqualTo("hello");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new IncomingInteraction(
                    "update-1",
                    new ExternalIdentityReference("other", "subject"),
                    PRIVATE_DESTINATION,
                    ChannelInteractionKind.CONVERSATION,
                    new ChannelInput.Text("hello"),
                    "request-1"));
    assertThatIllegalArgumentException().isThrownBy(() -> new ChannelInput.Text(" "));
  }

  @Test
  void keepsInteractiveValuesImmutableAndMakesApprovalBindingExplicit() {
    Map<String, String> values = new HashMap<>(Map.of("answer", "approve"));
    ChannelInput.Action action = new ChannelInput.Action("approval-approve", values);
    values.clear();
    ApprovalOperation operation =
        new ApprovalOperation("filesystem.delete", "document-42", Map.of("recursive", "false"));
    ApprovalRequest approval =
        new ApprovalRequest(
            "pending-1", "actor-1", operation, "Delete document?", "approve", "deny");

    assertThat(action.values()).containsEntry("answer", "approve").isUnmodifiable();
    assertThat(approval.operation().target()).isEqualTo("document-42");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new ApprovalRequest(
                    "pending-1", "actor-1", operation, "Delete document?", "same", "same"));
  }

  @Test
  void bindsInboundReceiverAndDeliversAllSemanticEventVariants() {
    AtomicReference<IncomingInteraction> received = new AtomicReference<>();
    RecordingChannel channel = new RecordingChannel();
    channel.bind(received::set);
    IncomingInteraction interaction =
        new IncomingInteraction(
            "update-1",
            new ExternalIdentityReference("telegram", "subject"),
            PRIVATE_DESTINATION,
            ChannelInteractionKind.CONVERSATION,
            new ChannelInput.Text("hello"),
            "request-1");
    channel.receiver.receive(interaction);
    ApprovalRequest approval =
        new ApprovalRequest(
            "pending-1",
            "actor-1",
            new ApprovalOperation("tool.execute", "tool-1", Map.of()),
            "Run tool?",
            "approve",
            "deny");

    channel.deliver(new ChannelEvent.Text(PRIVATE_DESTINATION, "answer", "request-1"));
    channel.deliver(new ChannelEvent.Progress(PRIVATE_DESTINATION, "working", "request-1"));
    channel.deliver(new ChannelEvent.Result(PRIVATE_DESTINATION, "done", "request-1"));
    channel.deliver(new ChannelEvent.Error(PRIVATE_DESTINATION, "failed", "request-1"));
    channel.deliver(new ChannelEvent.ApprovalPrompt(PRIVATE_DESTINATION, approval, "request-1"));

    assertThat(received.get()).isSameAs(interaction);
    assertThat(channel.events)
        .hasSize(5)
        .allMatch(event -> event.destination().privacy() == ChannelPrivacy.PRIVATE);
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ChannelEvent.Text(PRIVATE_DESTINATION, " ", "request-1"));
  }

  private static final class FixtureModule implements ZalavaModule {
    @Override
    public org.zalava.ModuleDescriptor descriptor() {
      return new org.zalava.ModuleDescriptor("module", "1", "Module", "fixture");
    }

    @Override
    public java.util.List<org.zalava.ProviderFactory> providerFactories() {
      return java.util.List.of();
    }
  }

  private static final class RecordingChannel implements ZalavaChannel {
    private ChannelInteractionReceiver receiver;
    private final java.util.List<ChannelEvent> events = new java.util.ArrayList<>();

    @Override
    public ChannelDescriptor descriptor() {
      return new ChannelDescriptor("telegram", "Telegram", ChannelCapabilities.textOnly());
    }

    @Override
    public void bind(ChannelInteractionReceiver receiver) {
      this.receiver = receiver;
    }

    @Override
    public void deliver(ChannelEvent event) {
      events.add(event);
    }
  }
}
