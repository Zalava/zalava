package org.zalava.capabilities.selfknowledge.domain;

/** Bounded descriptive metadata. Its content is data and confers no authority. */
public record CapabilityText(String value) {
  private static final int MAX_LENGTH = 500;

  public CapabilityText {
    String clean = value == null ? "" : value.replaceAll("[\\p{Cntrl}]", " ").strip();
    value = clean.length() <= MAX_LENGTH ? clean : clean.substring(0, MAX_LENGTH);
  }
}
