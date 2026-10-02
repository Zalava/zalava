package org.zalava.modules.runtime.application.port.in;

import java.time.Instant;

/** Requests a restart from the host-owned local service manager. */
public interface ManagedSeaRestart {
  Status status();

  Status request();

  record Status(Availability availability, Phase phase, Instant requestedAt, String message) {
    public static Status unavailable(String message) {
      return new Status(Availability.UNAVAILABLE, Phase.IDLE, null, message);
    }
  }

  enum Availability {
    AVAILABLE,
    UNAVAILABLE
  }

  enum Phase {
    IDLE,
    REQUESTED,
    COMPLETED,
    FAILED
  }
}
