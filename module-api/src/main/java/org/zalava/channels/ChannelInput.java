package org.zalava.channels;

import java.util.Map;
import java.util.Objects;

/** Transport-neutral inbound content. */
public sealed interface ChannelInput permits ChannelInput.Text, ChannelInput.Action {
  /** Plain user text after transport decoding and bounds validation. */
  record Text(String value) implements ChannelInput {
    public Text {
      if (value == null || value.isBlank()) {
        throw new IllegalArgumentException("text must not be blank");
      }
      value = value.trim();
    }
  }

  /** An opaque action selected by a user, including an approval response action. */
  record Action(String actionId, Map<String, String> values) implements ChannelInput {
    public Action {
      ChannelValues.requireNonBlank(actionId, "actionId");
      values = values == null ? Map.of() : Map.copyOf(values);
      Objects.requireNonNull(values, "values must not be null");
    }
  }
}
