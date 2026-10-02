package org.zalava.assistant.channels.runtime.domain;

/** Observable ingress decision without exposing identity or content in diagnostics. */
public sealed interface ChannelIngressResult
    permits ChannelIngressResult.Accepted, ChannelIngressResult.Denied {
  record Accepted(ResolvedChannelInteraction interaction) implements ChannelIngressResult {}

  record Denied(Reason reason) implements ChannelIngressResult {}

  enum Reason {
    UNKNOWN_CHANNEL,
    IDENTITY_DENIED,
    DUPLICATE,
    UNSUPPORTED_CAPABILITY
  }
}
