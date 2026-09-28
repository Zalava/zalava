package org.zalava.content;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/** Opaque, one-shot stream handle with a host-selected byte ceiling and no path access. */
public final class ContentSourceInput {
  private final InputStream source;
  private final long maximumBytes;
  private boolean opened;

  private ContentSourceInput(InputStream source, long maximumBytes) {
    this.source = Objects.requireNonNull(source, "source must not be null");
    if (maximumBytes < 1) throw new IllegalArgumentException("maximumBytes must be positive");
    this.maximumBytes = maximumBytes;
  }

  public static ContentSourceInput singleUse(InputStream source, long maximumBytes) {
    return new ContentSourceInput(source, maximumBytes);
  }

  public synchronized InputStream openStream() {
    if (opened) throw new IllegalStateException("source input is single-use");
    opened = true;
    return new BoundedInputStream(source, maximumBytes);
  }

  public long maximumBytes() {
    return maximumBytes;
  }

  private static final class BoundedInputStream extends FilterInputStream {
    private final long maximumBytes;
    private long readBytes;

    private BoundedInputStream(InputStream source, long maximumBytes) {
      super(source);
      this.maximumBytes = maximumBytes;
    }

    @Override
    public int read() throws IOException {
      int value = super.read();
      if (value != -1) increment(1);
      return value;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      int value = super.read(buffer, offset, length);
      if (value > 0) increment(value);
      return value;
    }

    private void increment(long amount) throws IOException {
      readBytes += amount;
      if (readBytes > maximumBytes) throw new IOException("source input exceeds maximumBytes");
    }
  }
}
