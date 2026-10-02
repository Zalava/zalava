package org.zalava.assistant.channels.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.zalava.api.*;
import org.zalava.api.extensions.channels.*;
import org.zalava.assistant.channels.runtime.domain.ChannelIngressResult;
import org.zalava.assistant.chat.application.port.in.ActorChatCommands;
import org.zalava.assistant.chat.domain.ActorChatTurn;
import org.zalava.identity.accounts.domain.AccountId;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.identity.channels.application.port.in.ChannelIdentityLinks;
import org.zalava.modules.catalog.FileSystemModuleConfigurationStore;
import org.zalava.modules.catalog.ModuleConfigurationSnapshot;
import org.zalava.modules.runtime.SeaRuntime;

class ChannelRuntimeConfigurationTest {
  @Test
  void activeModuleStartsWithScopedConfigurationAndDeliversTheAuthenticatedChatReply() {
    var identities = mock(ChannelIdentityLinks.class);
    var chats = mock(ActorChatCommands.class);
    var modules = mock(SeaRuntime.class);
    var configurations = mock(FileSystemModuleConfigurationStore.class);
    var module = mock(ZalavaModule.class);
    var channel = mock(ZalavaChannel.class);
    var actor = new Actor(AccountId.newId());
    when(module.descriptor())
        .thenReturn(new ModuleDescriptor("telegram-module", "1", "Telegram", "Channel fixture"));
    when(module.channels()).thenReturn(List.of(channel));
    when(modules.activeModules()).thenReturn(List.of(module));
    when(channel.descriptor())
        .thenReturn(new ChannelDescriptor("telegram", "Telegram", ChannelCapabilities.textOnly()));
    var snapshot =
        new ModuleConfigurationSnapshot(
            "telegram-module",
            "1",
            "schema",
            Map.of("botTokenRef", "secret-ref"),
            Map.of("botTokenRef", "secret-ref"));
    when(configurations.active("telegram-module")).thenReturn(Optional.of(snapshot));
    FactorySecretAccess secrets = reference -> Optional.of("token".toCharArray());
    when(configurations.secrets("telegram-module")).thenReturn(secrets);
    when(identities.resolve(any(), eq("chat:send"))).thenReturn(Optional.of(actor));
    when(chats.chat(eq(actor), any(), eq("hello")))
        .thenAnswer(call -> new ActorChatTurn("reply", List.of()));
    var runtime =
        new ChannelRuntimeConfiguration()
            .channelRuntime(identities, chats, modules, configurations);
    verify(channel)
        .start(
            argThat(
                context ->
                    context.configuration().equals(snapshot.factories())
                        && context.secrets() == secrets));
    var destination = new ChannelDestination("telegram", "private-chat", ChannelPrivacy.PRIVATE);
    var interaction =
        new IncomingInteraction(
            "message",
            new ExternalIdentityReference("telegram", "subject"),
            destination,
            ChannelInteractionKind.CONVERSATION,
            new ChannelInput.Text("hello"),
            "correlation");
    assertThat(runtime.receive(interaction)).isInstanceOf(ChannelIngressResult.Accepted.class);
    verify(channel).deliver(new ChannelEvent.Text(destination, "reply", "correlation"));
    runtime.close();
    verify(channel).close();
  }
}
