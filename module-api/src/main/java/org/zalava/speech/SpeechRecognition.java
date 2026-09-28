package org.zalava.speech;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import org.zalava.SeaServiceContract;

/**
 * SEA-owned typed service contract for one speech recognition provider supplied by a module.
 *
 * <p>SEA owns provider selection and scoping; a recognition provider receives only audio accepted
 * through its session, bounded by its declared capabilities. Sessions are {@link AutoCloseable};
 * the provider must release every resource on close. Contract version 1.
 */
public interface SpeechRecognition {

  SeaServiceContract<SpeechRecognition> CONTRACT =
      new SeaServiceContract<>("speech-recognition", "1", SpeechRecognition.class);

  /** The formats, streaming support, and bounds this provider declares. */
  SpeechCapabilities capabilities();

  /** Opens one bounded recognition session; fails with the typed exception when unsupported. */
  RecognitionSession open(Request request);

  /** One bounded, cancellable recognition exchange. */
  interface RecognitionSession extends AutoCloseable {

    /** Accepts one chunk of audio in the negotiated format. */
    void acceptChunk(byte[] chunk);

    /** Ends input and produces the transcript, honoring the request deadline. */
    Transcript endInput();

    /** Cancels the session; later operations fail with the typed exception. */
    void cancel();

    /** Idempotent resource closure; operations after close fail with the typed exception. */
    @Override
    void close();
  }

  /** One recognition request: negotiated formats, byte bound, and completion deadline. */
  record Request(Set<SpeechAudioFormat> formats, long maxBytes, Duration deadline) {

    public Request {
      formats = formats == null ? Set.of() : Set.copyOf(formats);
      if (maxBytes < 1) {
        throw new IllegalArgumentException("maxBytes must be positive");
      }
      Objects.requireNonNull(deadline, "deadline");
      if (deadline.isNegative() || deadline.isZero()) {
        throw new IllegalArgumentException("deadline must be positive");
      }
    }
  }

  /** The deterministic product of a completed recognition. */
  record Transcript(String text) {

    public Transcript {
      if (text == null || text.isBlank()) {
        throw new SpeechException("Recognition produced no transcript");
      }
      text = text.trim();
    }
  }
}
