package org.zalava.assistant.conversation.application;

import java.util.List;
import org.zalava.assistant.conversation.application.port.in.ActorConversations;
import org.zalava.assistant.conversation.application.port.in.ConversationContinuation;
import org.zalava.assistant.conversation.application.port.out.ConversationOriginStore;
import org.zalava.assistant.conversation.domain.ConversationMessage;
import org.zalava.assistant.conversation.domain.ConversationOrigin;
import org.zalava.assistant.conversation.domain.ConversationReference;
import org.zalava.identity.accounts.application.port.out.AccountStore;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.identity.channels.application.port.in.ChannelIdentityLinks;
import org.zalava.identity.channels.domain.ExternalChannelIdentity;

public final class DefaultConversationContinuation implements ConversationContinuation {
  private final ActorConversations conversations;
  private final ConversationOriginStore origins;
  private final ChannelIdentityLinks links;
  private final AccountStore accounts;

  public DefaultConversationContinuation(
      ActorConversations conversations,
      ConversationOriginStore origins,
      ChannelIdentityLinks links,
      AccountStore accounts) {
    this.conversations = conversations;
    this.origins = origins;
    this.links = links;
    this.accounts = accounts;
  }

  @Override
  public synchronized ConversationReference channelConversation(
      Actor actor, ConversationOrigin origin) {
    requireEnabled(actor);
    if (origin.webChat() || !linked(actor, origin))
      throw new IllegalArgumentException("Channel identity is unavailable");
    for (ConversationReference reference : conversations.findReferences(actor)) {
      if (origins.origin(actor, reference).filter(origin::equals).isPresent()) return reference;
    }
    ConversationReference reference = ConversationReference.newReference();
    conversations.saveAll(actor, reference, List.of());
    origins.saveOrigin(actor, reference, origin);
    return reference;
  }

  @Override
  public ConversationOrigin origin(Actor actor, ConversationReference reference) {
    requireOwned(actor, reference);
    return origins.origin(actor, reference).orElseGet(ConversationOrigin::web);
  }

  @Override
  public boolean canContinue(Actor actor, ConversationReference reference) {
    ConversationOrigin origin = origin(actor, reference);
    return origin.privateDestination() && (origin.webChat() || linked(actor, origin));
  }

  @Override
  public synchronized ConversationReference continueOnWeb(
      Actor actor, ConversationReference source, String destination) {
    if (!"web".equals(destination))
      throw new IllegalArgumentException("Unsupported continuation destination");
    if (!canContinue(actor, source))
      throw new IllegalArgumentException("Conversation cannot continue to this destination");
    List<ConversationMessage> history =
        conversations.findByReference(actor, source).stream()
            .filter(message -> message.role() != ConversationMessage.Role.SYSTEM)
            .toList();
    ConversationReference destinationReference = ConversationReference.newReference();
    conversations.saveAll(actor, destinationReference, history);
    origins.saveOrigin(actor, destinationReference, ConversationOrigin.web());
    return destinationReference;
  }

  @Override
  public void requireWeb(Actor actor, ConversationReference reference) {
    if (!origin(actor, reference).webChat())
      throw new IllegalArgumentException("Explicit web continuation is required");
  }

  private boolean linked(Actor actor, ConversationOrigin origin) {
    return links
        .resolve(new ExternalChannelIdentity(origin.channelId(), origin.subject()), "chat:send")
        .filter(actor::equals)
        .isPresent();
  }

  private void requireOwned(Actor actor, ConversationReference reference) {
    requireEnabled(actor);
    if (!conversations.findReferences(actor).contains(reference))
      throw new IllegalArgumentException("Conversation is unavailable");
  }

  private void requireEnabled(Actor actor) {
    accounts
        .findById(actor.accountId())
        .filter(account -> account.enabled())
        .orElseThrow(() -> new IllegalStateException("Actor is unavailable"));
  }
}
