package org.zalava.fixture.speech;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.zalava.api.extensions.speech.SpeechCapabilities;
import org.zalava.api.extensions.speech.SpeechException;
import org.zalava.api.extensions.speech.SpeechSynthesis;

/** Deterministic synthesis provider that streams scoped text as bounded audio chunks. */
public final class MockSpeechSynthesis implements SpeechSynthesis, AutoCloseable {

  private final SpeechCapabilities capabilities;
  private final int chunkSizeBytes;
  private boolean closed;

  public MockSpeechSynthesis(SpeechCapabilities capabilities, int chunkSizeBytes) {
    if (chunkSizeBytes < 1) {
      throw new SpeechException.ConfigurationException("chunkSizeBytes must be positive");
    }
    this.capabilities = capabilities;
    this.chunkSizeBytes = chunkSizeBytes;
  }

  @Override
  public SpeechCapabilities capabilities() {
    return capabilities;
  }

  @Override
  public SynthesisSession open(Request request) {
    if (closed) {
      throw new SpeechException.ClosedSessionException("Synthesis provider is closed");
    }
    if (!capabilities.formats().contains(request.format())) {
      throw new SpeechException.UnsupportedFormatException(
          "Unsupported synthesis format: " + request.format().key());
    }
    if (request.maxBytes() > capabilities.maxBytes()) {
      throw new SpeechException.LimitExceededException(
          "Requested byte bound exceeds the provider maximum");
    }
    return new Session(request);
  }

  @Override
  public void close() {
    closed = true;
  }

  private final class Session implements SynthesisSession {
    private final Request request;
    private final long deadlineNanos;
    private final byte[] audio;
    private int offset;
    private long producedBytes;
    private boolean cancelled;
    private boolean sessionClosed;

    private Session(Request request) {
      this.request = request;
      this.deadlineNanos = System.nanoTime() + request.deadline().toNanos();
      this.audio = request.text().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public byte[] nextChunk() {
      ensureOpen();
      if (cancelled) {
        throw new SpeechException.CancelledException("Synthesis session was cancelled");
      }
      ensureDeadline();
      if (offset >= audio.length) {
        return null;
      }
      int length = Math.min(chunkSizeBytes, audio.length - offset);
      if (producedBytes + length > request.maxBytes()) {
        throw new SpeechException.LimitExceededException(
            "Synthesis output exceeds the requested byte bound");
      }
      byte[] chunk = Arrays.copyOfRange(audio, offset, offset + length);
      offset += length;
      producedBytes += length;
      return chunk;
    }

    @Override
    public Completion awaitCompletion() {
      ensureOpen();
      if (cancelled) {
        throw new SpeechException.CancelledException("Synthesis session was cancelled");
      }
      ensureDeadline();
      long remaining = audio.length - offset;
      if (producedBytes + remaining > request.maxBytes()) {
        throw new SpeechException.LimitExceededException(
            "Synthesis output exceeds the requested byte bound");
      }
      producedBytes += remaining;
      offset = audio.length;
      return new Completion(request.format().key(), producedBytes);
    }

    @Override
    public void cancel() {
      ensureOpen();
      cancelled = true;
    }

    @Override
    public void close() {
      sessionClosed = true;
    }

    private void ensureOpen() {
      if (sessionClosed) {
        throw new SpeechException.ClosedSessionException("Synthesis session is closed");
      }
    }

    private void ensureDeadline() {
      if (System.nanoTime() > deadlineNanos) {
        throw new SpeechException.DeadlineExceededException("Synthesis deadline exceeded");
      }
    }
  }
}
