package org.zalava.channelruntime.application;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.channelruntime.application.port.in.ChannelInteractionDispatcher;
import org.zalava.channelruntime.application.port.in.ChannelRuntime;
import org.zalava.channelruntime.domain.ChannelIngressResult;
import org.zalava.channelruntime.domain.ResolvedChannelInteraction;
import org.zalava.channels.ChannelContentPrivacy;
import org.zalava.channels.ChannelEvent;
import org.zalava.channels.ChannelInteractionKind;
import org.zalava.channels.ChannelPrivacy;
import org.zalava.channels.IncomingInteraction;
import org.zalava.channels.ZalavaChannel;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.identity.channels.application.port.in.ChannelIdentityLinks;
import org.zalava.identity.channels.domain.ExternalChannelIdentity;

/** In-process Core registry. Identity and policy are rechecked on every ingress. */
public final class DefaultChannelRuntime implements ChannelRuntime {
  private final ChannelIdentityLinks identities;
  private final ChannelInteractionDispatcher dispatcher;
  private final Map<String, ZalavaChannel> channels = new ConcurrentHashMap<>();
  private final Map<ConversationKey, ConversationReference> conversations =
      new ConcurrentHashMap<>();
  private final Set<String> received = ConcurrentHashMap.newKeySet();

  public DefaultChannelRuntime(
      ChannelIdentityLinks identities, ChannelInteractionDispatcher dispatcher) {
    this.identities = Objects.requireNonNull(identities, "identities must not be null");
    this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
  }

  @Override
  public void register(ZalavaChannel channel) {
    Objects.requireNonNull(channel, "channel must not be null");
    String id = channel.descriptor().channelId();
    if (channels.putIfAbsent(id, channel) != null)
      throw new IllegalArgumentException("Channel already registered: " + id);
    channel.bind(this::receive);
  }

  @Override
  public ChannelIngressResult receive(IncomingInteraction interaction) {
    Objects.requireNonNull(interaction, "interaction must not be null");
    ZalavaChannel channel = channels.get(interaction.destination().channelId());
    if (channel == null
        || !channel.descriptor().channelId().equals(interaction.identity().channelId()))
      return new ChannelIngressResult.Denied(ChannelIngressResult.Reason.UNKNOWN_CHANNEL);
    if (!supports(channel, interaction.kind()))
      return new ChannelIngressResult.Denied(ChannelIngressResult.Reason.UNSUPPORTED_CAPABILITY);
    String key = interaction.identity().channelId() + ":" + interaction.interactionId();
    if (!received.add(key))
      return new ChannelIngressResult.Denied(ChannelIngressResult.Reason.DUPLICATE);
    Actor actor =
        identities.resolve(identity(interaction), operation(interaction.kind())).orElse(null);
    if (actor == null) {
      received.remove(key);
      return new ChannelIngressResult.Denied(ChannelIngressResult.Reason.IDENTITY_DENIED);
    }
    ConversationReference conversation =
        conversations.computeIfAbsent(
            new ConversationKey(
                actor,
                interaction.destination().channelId(),
                interaction.destination().deliveryHandle()),
            ignored -> ConversationReference.newReference());
    var resolved = new ResolvedChannelInteraction(actor, conversation, interaction);
    try {
      dispatcher.dispatch(resolved);
    } catch (RuntimeException exception) {
      received.remove(key);
      throw exception;
    }
    return new ChannelIngressResult.Accepted(resolved);
  }

  @Override
  public boolean deliver(ChannelEvent event) {
    Objects.requireNonNull(event, "event must not be null");
    ZalavaChannel channel = channels.get(event.destination().channelId());
    if (channel == null || !supports(channel, event) || privateOutputToShared(event)) return false;
    channel.deliver(event);
    return true;
  }

  @Override
  public void close() {
    channels.values().forEach(ZalavaChannel::close);
    channels.clear();
    conversations.clear();
    received.clear();
  }

  private static ExternalChannelIdentity identity(IncomingInteraction interaction) {
    return new ExternalChannelIdentity(
        interaction.identity().channelId(), interaction.identity().subject());
  }

  private static String operation(ChannelInteractionKind kind) {
    return kind == ChannelInteractionKind.APPROVAL_RESPONSE ? "approval:respond" : "chat:send";
  }

  private static boolean supports(ZalavaChannel channel, ChannelInteractionKind kind) {
    return kind != ChannelInteractionKind.APPROVAL_RESPONSE
        || channel.descriptor().capabilities().approvalPrompts();
  }

  private static boolean supports(ZalavaChannel channel, ChannelEvent event) {
    var c = channel.descriptor().capabilities();
    if (event instanceof ChannelEvent.ApprovalPrompt) return c.approvalPrompts();
    if (event instanceof ChannelEvent.Progress) return c.streaming();
    return c.text();
  }

  private static boolean privateOutputToShared(ChannelEvent event) {
    return event.destination().privacy() == ChannelPrivacy.SHARED
        && event.privacy() == ChannelContentPrivacy.PRIVATE;
  }

  private record ConversationKey(Actor actor, String channelId, String destination) {}
}
