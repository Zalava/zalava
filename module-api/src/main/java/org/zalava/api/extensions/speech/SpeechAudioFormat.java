package org.zalava.api.extensions.speech;

import java.util.Objects;

/** One negotiable audio format: container, codec, and sample rate. */
public record SpeechAudioFormat(String container, String codec, int sampleRateHz) {

  public SpeechAudioFormat {
    Objects.requireNonNull(container, "container");
    Objects.requireNonNull(codec, "codec");
    if (container.isBlank() || codec.isBlank()) {
      throw new IllegalArgumentException("container and codec must not be blank");
    }
    if (sampleRateHz < 1) {
      throw new IllegalArgumentException("sampleRateHz must be positive");
    }
  }

  /** Canonical negotiation key used for format matching. */
  public String key() {
    return container + "/" + codec + "/" + sampleRateHz;
  }
}
