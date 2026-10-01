package org.zalava.channelruntime.application.port.in;

import org.zalava.channelruntime.domain.ResolvedChannelInteraction;

/** SEA-owned continuation port; transport modules never invoke an agent directly. */
@FunctionalInterface
public interface ChannelInteractionDispatcher {
  void dispatch(ResolvedChannelInteraction interaction);
}
