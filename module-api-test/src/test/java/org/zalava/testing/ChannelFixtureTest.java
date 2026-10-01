package org.zalava.testing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.zalava.ModuleDescriptor;
import org.zalava.ProviderFactory;
import org.zalava.ZalavaModule;
import org.zalava.channels.ChannelCapabilities;
import org.zalava.channels.ChannelDescriptor;
import org.zalava.channels.ChannelDestination;
import org.zalava.channels.ChannelEvent;
import org.zalava.channels.ChannelInput;
import org.zalava.channels.ChannelInteractionKind;
import org.zalava.channels.ChannelPrivacy;
import org.zalava.channels.ExternalIdentityReference;
import org.zalava.channels.IncomingInteraction;
import org.zalava.channels.ZalavaChannel;

class ChannelFixtureTest {
  @Test
  void bindsIngressAndDeliversSemanticEventsForAModuleChannel() {
    AtomicReference<IncomingInteraction> received = new AtomicReference<>();
    FixtureChannel channel = new FixtureChannel();
    ChannelFixture fixture = ChannelFixture.bind(new FixtureModule(channel), received::set);
    IncomingInteraction interaction =
        new IncomingInteraction(
            "interaction-1",
            new ExternalIdentityReference("fixture-channel", "subject-1"),
            new ChannelDestination("fixture-channel", "destination-1", ChannelPrivacy.PRIVATE),
            ChannelInteractionKind.CONVERSATION,
            new ChannelInput.Text("hello"),
            "correlation-1");

    channel.receiver.receive(interaction);
    fixture.deliver(
        "fixture-channel",
        new ChannelEvent.Text(interaction.destination(), "response", interaction.correlationId()));

    assertThat(fixture.channels()).containsExactly(channel);
    assertThat(received.get()).isEqualTo(interaction);
    assertThat(channel.delivered).hasSize(1);
    assertThatIllegalArgumentException().isThrownBy(() -> fixture.requireChannel("missing"));
  }

  private record FixtureModule(FixtureChannel channel) implements ZalavaModule {
    @Override
    public ModuleDescriptor descriptor() {
      return new ModuleDescriptor("fixture", "1", "Fixture", "fixture");
    }

    @Override
    public List<ProviderFactory> providerFactories() {
      return List.of();
    }

    @Override
    public List<ZalavaChannel> channels() {
      return List.of(channel);
    }
  }

  private static final class FixtureChannel implements ZalavaChannel {
    private org.zalava.channels.ChannelInteractionReceiver receiver;
    private final List<ChannelEvent> delivered = new java.util.ArrayList<>();

    @Override
    public ChannelDescriptor descriptor() {
      return new ChannelDescriptor(
          "fixture-channel",
          "Fixture channel",
          new ChannelCapabilities(true, false, false, false, false, false, false, false));
    }

    @Override
    public void bind(org.zalava.channels.ChannelInteractionReceiver receiver) {
      this.receiver = receiver;
    }

    @Override
    public void deliver(ChannelEvent event) {
      delivered.add(event);
    }
  }
}
