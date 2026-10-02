package org.zalava.api.extensions.channels;

/** Semantic purpose of an inbound interaction; transport message shapes are adapter-local. */
public enum ChannelInteractionKind {
  CONVERSATION,
  INTERACTIVE_ACTION,
  APPROVAL_RESPONSE
}
