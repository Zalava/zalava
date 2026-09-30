package org.zalava.channels;

final class ChannelValues {
  private ChannelValues() {}

  static void requireNonBlank(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
  }
}
