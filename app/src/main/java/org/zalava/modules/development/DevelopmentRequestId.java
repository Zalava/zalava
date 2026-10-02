package org.zalava.modules.development;

import java.util.Objects;
import java.util.regex.Pattern;

public record DevelopmentRequestId(String value) {

  private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_-]{0,127}");

  public DevelopmentRequestId {
    Objects.requireNonNull(value, "value must not be null");
    if (!VALID_ID.matcher(value).matches()) {
      throw new IllegalArgumentException("development request id is invalid: " + value);
    }
  }
}
