package org.zalava.assistant.channels.runtime;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalava.api.extensions.channels.ChannelEvent;
import org.zalava.api.extensions.channels.ChannelInput;
import org.zalava.api.extensions.channels.ChannelTransportContext;
import org.zalava.assistant.channels.runtime.application.DefaultChannelRuntime;
import org.zalava.assistant.channels.runtime.application.port.in.ChannelRuntime;
import org.zalava.assistant.chat.application.port.in.ActorChatCommands;
import org.zalava.identity.channels.application.port.in.ChannelIdentityLinks;
import org.zalava.modules.catalog.FileSystemModuleConfigurationStore;
import org.zalava.modules.runtime.ZalavaRuntime;

/**
 * Composition boundary for module channel transports and the Zalava-owned chat continuation path.
 */
@Configuration
public class ChannelRuntimeConfiguration {
  @Bean(destroyMethod = "close")
  ChannelRuntime channelRuntime(
      ChannelIdentityLinks identities,
      ActorChatCommands chats,
      ZalavaRuntime modules,
      FileSystemModuleConfigurationStore configurations,
      org.zalava.assistant.conversation.application.port.in.ConversationContinuation continuation) {
    AtomicReference<DefaultChannelRuntime> runtime = new AtomicReference<>();
    DefaultChannelRuntime created =
        new DefaultChannelRuntime(
            identities,
            interaction -> {
              if (interaction.interaction().input() instanceof ChannelInput.Text text) {
                var turn =
                    chats.chat(interaction.actor(), interaction.conversation(), text.value());
                runtime
                    .get()
                    .deliver(
                        new ChannelEvent.Text(
                            interaction.interaction().destination(),
                            turn.text(),
                            interaction.interaction().correlationId()));
              }
            },
            continuation::channelConversation);
    runtime.set(created);
    modules
        .activeModules()
        .forEach(
            module -> {
              var configuration = configurations.active(module.descriptor().moduleId());
              ChannelTransportContext context =
                  configuration
                      .map(
                          snapshot ->
                              new ChannelTransportContext(
                                  snapshot.factories(),
                                  configurations.secrets(module.descriptor().moduleId())))
                      .orElseGet(ChannelTransportContext::empty);
              module
                  .channels()
                  .forEach(
                      channel -> {
                        channel.start(context);
                        created.register(channel);
                      });
            });
    return created;
  }
}
