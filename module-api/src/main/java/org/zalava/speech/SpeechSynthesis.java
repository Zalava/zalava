package org.zalava.speech;

import java.time.Duration;
import java.util.Objects;
import org.zalava.ZalavaServiceContract;

/**
 * SEA-owned typed service contract for one speech synthesis provider supplied by a module.
 *
 * <p>SEA owns provider selection and scoping; a synthesis provider streams only bounded audio
 * declared in its capabilities and releases every resource on session close. Contract version 1.
 */
public interface SpeechSynthesis {

  ZalavaServiceContract<SpeechSynthesis> CONTRACT =
      new ZalavaServiceContract<>("speech-synthesis", "1", SpeechSynthesis.class);

  /** The formats, streaming support, and bounds this provider declares. */
  SpeechCapabilities capabilities();

  /** Opens one bounded synthesis session; fails with the typed exception when unsupported. */
  SynthesisSession open(Request request);

  /** One bounded, cancellable, streaming synthesis. */
  interface SynthesisSession extends AutoCloseable {

    /** Returns the next bounded chunk, or {@code null} when the stream is exhausted. */
    byte[] nextChunk();

    /** Waits for synthesis completion and returns the result metadata. */
    Completion awaitCompletion();

    /** Cancels the session; later operations fail with the typed exception. */
    void cancel();

    /** Idempotent resource closure; operations after close fail with the typed exception. */
    @Override
    void close();
  }

  /** One synthesis request: negotiated format, text, byte bound, and completion deadline. */
  record Request(SpeechAudioFormat format, String text, long maxBytes, Duration deadline) {

    public Request {
      Objects.requireNonNull(format, "format");
      Objects.requireNonNull(text, "text");
      if (text.isBlank()) {
        throw new IllegalArgumentException("text must not be blank");
      }
      if (maxBytes < 1) {
        throw new IllegalArgumentException("maxBytes must be positive");
      }
      Objects.requireNonNull(deadline, "deadline");
      if (deadline.isNegative() || deadline.isZero()) {
        throw new IllegalArgumentException("deadline must be positive");
      }
    }
  }

  /** The deterministic metadata of a completed synthesis. */
  record Completion(String formatKey, long totalBytes) {

    public Completion {
      if (formatKey == null || formatKey.isBlank()) {
        throw new IllegalArgumentException("formatKey must not be blank");
      }
      if (totalBytes < 1) {
        throw new IllegalArgumentException("totalBytes must be positive");
      }
    }
  }
}
