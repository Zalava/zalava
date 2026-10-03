package org.zalava.assistant.conversation.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.assistant.conversation.adapter.out.filesystem.FileSystemConversationStore;
import org.zalava.assistant.conversation.domain.*;
import org.zalava.identity.accounts.application.port.out.AccountStore;
import org.zalava.identity.accounts.domain.*;
import org.zalava.identity.channels.application.port.in.ChannelIdentityLinks;

class DefaultConversationContinuationTest {
  @TempDir Path workspace;
  final Actor owner = new Actor(AccountId.newId());
  final Actor other = new Actor(AccountId.newId());
  final AccountStore accounts = mock(AccountStore.class);
  final ChannelIdentityLinks links = mock(ChannelIdentityLinks.class);
  final ConversationOrigin origin =
      new ConversationOrigin("telegram", "12345", "private-chat", true);
  FileSystemConversationStore store;
  DefaultConversationContinuation continuation;

  @BeforeEach
  void setup() {
    when(accounts.findById(owner.accountId())).thenReturn(Optional.of(account(owner, true)));
    when(accounts.findById(other.accountId())).thenReturn(Optional.of(account(other, true)));
    when(links.resolve(any(), eq("chat:send"))).thenReturn(Optional.of(owner));
    store = new FileSystemConversationStore(workspace);
    continuation = new DefaultConversationContinuation(store, store, links, accounts);
  }

  @Test
  void persistsOriginsAndReusesChannelHistoryAcrossStoreRecreation() {
    var source = continuation.channelConversation(owner, origin);
    var history =
        List.of(
            new ConversationMessage(ConversationMessage.Role.SYSTEM, "Private system instruction"),
            new ConversationMessage(ConversationMessage.Role.USER, "Channel question"),
            new ConversationMessage(ConversationMessage.Role.ASSISTANT, "Channel response"));
    store.saveAll(owner, source, history);
    store.appendAll(
        owner,
        source,
        List.of(new ConversationMessage(ConversationMessage.Role.USER, "Second question")));
    var reopened = new FileSystemConversationStore(workspace);
    var recreated = new DefaultConversationContinuation(reopened, reopened, links, accounts);
    assertThat(recreated.channelConversation(owner, origin)).isEqualTo(source);
    assertThat(recreated.origin(owner, source)).isEqualTo(origin);
    var target = recreated.continueOnWeb(owner, source, "web");
    assertThat(target).isNotEqualTo(source);
    assertThat(reopened.findByReference(owner, target))
        .hasSize(3)
        .noneMatch(message -> message.role() == ConversationMessage.Role.SYSTEM);
    assertThat(reopened.findByReference(owner, source)).hasSize(4);
    recreated.requireWeb(owner, target);
    assertThatThrownBy(() -> recreated.requireWeb(owner, source))
        .hasMessageContaining("Explicit web continuation");
    reopened.appendAll(
        owner,
        target,
        List.of(new ConversationMessage(ConversationMessage.Role.USER, "Web question")));
    assertThat(reopened.findByReference(owner, source)).hasSize(4);
    assertThat(reopened.origin(owner, target)).contains(ConversationOrigin.web());
  }

  @Test
  void rejectsForeignOwnersRevokedLinksSharedAndUnsupportedDestinations() {
    var source = continuation.channelConversation(owner, origin);
    assertThatThrownBy(() -> continuation.continueOnWeb(other, source, "web"))
        .hasMessageContaining("unavailable");
    assertThatThrownBy(() -> continuation.continueOnWeb(owner, source, "telegram"))
        .hasMessageContaining("Unsupported");
    when(links.resolve(any(), eq("chat:send"))).thenReturn(Optional.empty());
    assertThat(continuation.canContinue(owner, source)).isFalse();
    assertThatThrownBy(() -> continuation.continueOnWeb(owner, source, "web"))
        .hasMessageContaining("cannot continue");
    assertThatThrownBy(() -> continuation.channelConversation(owner, origin))
        .hasMessageContaining("unavailable");
    when(links.resolve(any(), eq("chat:send"))).thenReturn(Optional.of(other));
    assertThat(continuation.canContinue(owner, source)).isFalse();
    when(links.resolve(any(), eq("chat:send"))).thenReturn(Optional.of(owner));
    var shared =
        continuation.channelConversation(
            owner, new ConversationOrigin("telegram", "12345", "group-chat", false));
    assertThat(continuation.canContinue(owner, shared)).isFalse();
    assertThatThrownBy(() -> continuation.continueOnWeb(owner, shared, "web"))
        .hasMessageContaining("cannot continue");
    assertThat(store.findReferences(owner)).hasSize(2);
  }

  @Test
  void rejectsDisabledAccountsAndWebRoutingButPreservesExistingWebChats() {
    var web = ConversationReference.newReference();
    store.saveAll(owner, web, List.of());
    assertThat(continuation.origin(owner, web)).isEqualTo(ConversationOrigin.web());
    continuation.requireWeb(owner, web);
    assertThat(continuation.canContinue(owner, web)).isTrue();
    assertThatThrownBy(() -> continuation.channelConversation(owner, ConversationOrigin.web()))
        .hasMessageContaining("unavailable");
    when(accounts.findById(owner.accountId())).thenReturn(Optional.of(account(owner, false)));
    assertThatThrownBy(() -> continuation.continueOnWeb(owner, web, "web"))
        .hasMessageContaining("unavailable");
  }

  @Test
  void rejectsIncompleteOriginsAndMissingOriginTarget() {
    assertThatThrownBy(() -> new ConversationOrigin("telegram", "", "chat", true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ConversationOrigin("", "identity", "chat", true))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ConversationOrigin("telegram", null, "chat", true))
        .isInstanceOf(NullPointerException.class);
    var missing = ConversationReference.newReference();
    assertThat(store.origin(owner, missing)).isEmpty();
    assertThatThrownBy(() -> store.saveOrigin(owner, missing, origin))
        .hasMessageContaining("unavailable");
  }

  static Account account(Actor actor, boolean enabled) {
    return new Account(
        actor.accountId(),
        "account-" + actor.accountId(),
        "hash",
        enabled,
        AccountRole.MEMBER,
        false,
        Instant.EPOCH,
        Instant.EPOCH,
        0);
  }
}
