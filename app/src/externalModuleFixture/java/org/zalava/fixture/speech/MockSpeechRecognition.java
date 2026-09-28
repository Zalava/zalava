package org.zalava.fixture.speech;

import java.io.ByteArrayOutputStream;
import org.zalava.speech.SpeechCapabilities;
import org.zalava.speech.SpeechException;
import org.zalava.speech.SpeechRecognition;

/** Deterministic recognition provider that echoes scoped configuration as a transcript. */
public final class MockSpeechRecognition implements SpeechRecognition, AutoCloseable {

  private final SpeechCapabilities capabilities;
  private final String transcript;
  private boolean closed;

  public MockSpeechRecognition(SpeechCapabilities capabilities, String transcript) {
    this.capabilities = capabilities;
    this.transcript = transcript;
  }

  @Override
  public SpeechCapabilities capabilities() {
    return capabilities;
  }

  @Override
  public RecognitionSession open(Request request) {
    if (closed) {
      throw new SpeechException.ClosedSessionException("Recognition provider is closed");
    }
    if (!capabilities.supports(request.formats())) {
      throw new SpeechException.UnsupportedFormatException(
          "Unsupported recognition format: " + request.formats());
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

  private final class Session implements RecognitionSession {
    private final Request request;
    private final long deadlineNanos;
    private final ByteArrayOutputStream input = new ByteArrayOutputStream();
    private boolean cancelled;
    private boolean sessionClosed;

    private Session(Request request) {
      this.request = request;
      this.deadlineNanos = System.nanoTime() + request.deadline().toNanos();
    }

    @Override
    public void acceptChunk(byte[] chunk) {
      ensureOpen();
      if (cancelled) {
        throw new SpeechException.CancelledException("Recognition session was cancelled");
      }
      if (chunk == null) {
        throw new SpeechException("Recognition chunk must not be null");
      }
      if (input.size() + chunk.length > request.maxBytes()) {
        throw new SpeechException.LimitExceededException(
            "Recognition input exceeds the requested byte bound");
      }
      input.writeBytes(chunk);
      ensureDeadline();
    }

    @Override
    public Transcript endInput() {
      ensureOpen();
      if (cancelled) {
        throw new SpeechException.CancelledException("Recognition session was cancelled");
      }
      ensureDeadline();
      return new Transcript(transcript);
    }

    @Override
    public void cancel() {
      ensureOpen();
      cancelled = true;
    }

    @Override
    public void close() {
      sessionClosed = true;
      input.reset();
    }

    private void ensureOpen() {
      if (sessionClosed) {
        throw new SpeechException.ClosedSessionException("Recognition session is closed");
      }
    }

    private void ensureDeadline() {
      if (System.nanoTime() > deadlineNanos) {
        throw new SpeechException.DeadlineExceededException("Recognition deadline exceeded");
      }
    }
  }
}
