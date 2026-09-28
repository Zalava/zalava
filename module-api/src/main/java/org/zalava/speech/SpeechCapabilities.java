package org.zalava.speech;

import java.util.Objects;
import java.util.Set;

/** Declared provider capabilities: negotiable formats, streaming support, and byte bounds. */
public record SpeechCapabilities(Set<SpeechAudioFormat> formats, boolean streaming, long maxBytes) {

  public SpeechCapabilities {
    formats = formats == null ? Set.of() : Set.copyOf(formats);
    Objects.requireNonNull(formats, "formats");
    if (maxBytes < 1) {
      throw new IllegalArgumentException("maxBytes must be positive");
    }
  }

  /** True when every requested format is declared; empty requests negotiate to any format. */
  public boolean supports(Set<SpeechAudioFormat> requested) {
    return requested == null || requested.isEmpty() || formats.containsAll(requested);
  }
}
