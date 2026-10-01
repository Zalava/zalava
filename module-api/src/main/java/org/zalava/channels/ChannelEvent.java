package org.zalava.channels;

import java.util.Objects;

/** Semantic Core output delivered by a channel module after Core privacy and capability checks. */
public sealed interface ChannelEvent
    permits ChannelEvent.Text,
        ChannelEvent.Progress,
        ChannelEvent.Result,
        ChannelEvent.Error,
        ChannelEvent.ApprovalPrompt {
  ChannelDestination destination();

  String correlationId();

  record Text(ChannelDestination destination, String text, String correlationId)
      implements ChannelEvent {
    public Text {
      destination = Objects.requireNonNull(destination, "destination must not be null");
      ChannelValues.requireNonBlank(text, "text");
      ChannelValues.requireNonBlank(correlationId, "correlationId");
    }
  }

  record Progress(ChannelDestination destination, String message, String correlationId)
      implements ChannelEvent {
    public Progress {
      destination = Objects.requireNonNull(destination, "destination must not be null");
      ChannelValues.requireNonBlank(message, "message");
      ChannelValues.requireNonBlank(correlationId, "correlationId");
    }
  }

  record Result(ChannelDestination destination, String summary, String correlationId)
      implements ChannelEvent {
    public Result {
      destination = Objects.requireNonNull(destination, "destination must not be null");
      ChannelValues.requireNonBlank(summary, "summary");
      ChannelValues.requireNonBlank(correlationId, "correlationId");
    }
  }

  record Error(ChannelDestination destination, String message, String correlationId)
      implements ChannelEvent {
    public Error {
      destination = Objects.requireNonNull(destination, "destination must not be null");
      ChannelValues.requireNonBlank(message, "message");
      ChannelValues.requireNonBlank(correlationId, "correlationId");
    }
  }

  record ApprovalPrompt(
      ChannelDestination destination, ApprovalRequest approval, String correlationId)
      implements ChannelEvent {
    public ApprovalPrompt {
      destination = Objects.requireNonNull(destination, "destination must not be null");
      approval = Objects.requireNonNull(approval, "approval must not be null");
      ChannelValues.requireNonBlank(correlationId, "correlationId");
    }
  }
}
