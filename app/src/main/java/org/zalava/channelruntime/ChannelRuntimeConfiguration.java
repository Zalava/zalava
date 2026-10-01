package org.zalava.channelruntime;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalava.channelidentity.application.port.in.ChannelIdentityLinks;
import org.zalava.channelruntime.application.DefaultChannelRuntime;
import org.zalava.channelruntime.application.port.in.ChannelRuntime;
import org.zalava.channels.ChannelEvent;
import org.zalava.channels.ChannelInput;
import org.zalava.chat.application.port.in.ActorChatCommands;
import org.zalava.runtime.SeaModuleRegistry;

/** Composition boundary for module channel transports and the SEA-owned chat continuation path. */
@Configuration
public class ChannelRuntimeConfiguration {
  @Bean(destroyMethod = "close")
  ChannelRuntime channelRuntime(
      ChannelIdentityLinks identities, ActorChatCommands chats, SeaModuleRegistry modules) {
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
            });
    runtime.set(created);
    modules.modules().forEach(module -> created.registerAll(module.channels()));
    return created;
  }
}
