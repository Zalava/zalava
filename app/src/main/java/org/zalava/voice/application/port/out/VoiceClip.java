package org.zalava.voice.application.port.out;

import java.util.Arrays;

public final class VoiceClip {

  private final byte[] content;
  private final String mimeType;
  private final String source;

  public VoiceClip(byte[] content, String mimeType, String source) {
    this.content = Arrays.copyOf(content, content.length);
    this.mimeType = mimeType;
    this.source = source;
  }

  public byte[] content() {
    return Arrays.copyOf(content, content.length);
  }

  public String mimeType() {
    return mimeType;
  }

  public String source() {
    return source;
  }

  public long size() {
    return content.length;
  }
}
