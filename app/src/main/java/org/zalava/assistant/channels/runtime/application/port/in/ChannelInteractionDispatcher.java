package org.zalava.assistant.channels.runtime.application.port.in;

import org.zalava.assistant.channels.runtime.domain.ResolvedChannelInteraction;

/** SEA-owned continuation port; transport modules never invoke an agent directly. */
@FunctionalInterface
public interface ChannelInteractionDispatcher {
  void dispatch(ResolvedChannelInteraction interaction);
}
