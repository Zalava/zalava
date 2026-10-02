package org.zalava.api.extensions.channels;

import java.util.Objects;

/** Semantic Core output delivered by a channel module after Core privacy and capability checks. */
public sealed interface ChannelEvent
    permits ChannelEvent.Text,
        ChannelEvent.Progress,
        ChannelEvent.Result,
        ChannelEvent.Error,
        ChannelEvent.ApprovalPrompt {
  ChannelDestination destination();

  ChannelContentPrivacy privacy();

  String correlationId();

  record Text(
      ChannelDestination destination,
      String text,
      ChannelContentPrivacy privacy,
      String correlationId)
      implements ChannelEvent {
    public Text {
      destination = Objects.requireNonNull(destination, "destination must not be null");
      ChannelValues.requireNonBlank(text, "text");
      privacy = Objects.requireNonNull(privacy, "privacy must not be null");
      ChannelValues.requireNonBlank(correlationId, "correlationId");
    }

    public Text(ChannelDestination destination, String text, String correlationId) {
      this(destination, text, ChannelContentPrivacy.SHAREABLE, correlationId);
    }
  }

  record Progress(
      ChannelDestination destination,
      String message,
      ChannelContentPrivacy privacy,
      String correlationId)
      implements ChannelEvent {
    public Progress {
      destination = Objects.requireNonNull(destination, "destination must not be null");
      ChannelValues.requireNonBlank(message, "message");
      privacy = Objects.requireNonNull(privacy, "privacy must not be null");
      ChannelValues.requireNonBlank(correlationId, "correlationId");
    }

    public Progress(ChannelDestination destination, String message, String correlationId) {
      this(destination, message, ChannelContentPrivacy.SHAREABLE, correlationId);
    }
  }

  record Result(
      ChannelDestination destination,
      String summary,
      ChannelContentPrivacy privacy,
      String correlationId)
      implements ChannelEvent {
    public Result {
      destination = Objects.requireNonNull(destination, "destination must not be null");
      ChannelValues.requireNonBlank(summary, "summary");
      privacy = Objects.requireNonNull(privacy, "privacy must not be null");
      ChannelValues.requireNonBlank(correlationId, "correlationId");
    }

    public Result(ChannelDestination destination, String summary, String correlationId) {
      this(destination, summary, ChannelContentPrivacy.SHAREABLE, correlationId);
    }
  }

  record Error(
      ChannelDestination destination,
      String message,
      ChannelContentPrivacy privacy,
      String correlationId)
      implements ChannelEvent {
    public Error {
      destination = Objects.requireNonNull(destination, "destination must not be null");
      ChannelValues.requireNonBlank(message, "message");
      privacy = Objects.requireNonNull(privacy, "privacy must not be null");
      ChannelValues.requireNonBlank(correlationId, "correlationId");
    }

    public Error(ChannelDestination destination, String message, String correlationId) {
      this(destination, message, ChannelContentPrivacy.SHAREABLE, correlationId);
    }
  }

  record ApprovalPrompt(
      ChannelDestination destination,
      ApprovalRequest approval,
      ChannelContentPrivacy privacy,
      String correlationId)
      implements ChannelEvent {
    public ApprovalPrompt {
      destination = Objects.requireNonNull(destination, "destination must not be null");
      approval = Objects.requireNonNull(approval, "approval must not be null");
      privacy = Objects.requireNonNull(privacy, "privacy must not be null");
      ChannelValues.requireNonBlank(correlationId, "correlationId");
    }

    public ApprovalPrompt(
        ChannelDestination destination, ApprovalRequest approval, String correlationId) {
      this(destination, approval, ChannelContentPrivacy.PRIVATE, correlationId);
    }
  }
}
