package org.zalava.modules.runtime.adapter.out.actuator;

import org.springframework.cloud.context.restart.RestartEndpoint;
import org.springframework.context.ConfigurableApplicationContext;
import org.zalava.modules.runtime.adapter.out.filesystem.FileSystemManagedZalavaRestart;
import org.zalava.modules.runtime.application.port.in.ManagedZalavaRestart;

/** Uses Spring Cloud's maintained context restart, with a journal outside the manager signal. */
public final class ActuatorManagedZalavaRestart implements ManagedZalavaRestart {
  private final RestartEndpoint endpoint;
  private final FileSystemManagedZalavaRestart journal;

  public ActuatorManagedZalavaRestart(
      RestartEndpoint endpoint, FileSystemManagedZalavaRestart journal) {
    this.endpoint = endpoint;
    this.journal = journal;
  }

  @Override
  public synchronized Status status() {
    if (!endpoint.isRunning())
      return Status.unavailable(
          "Application restart is unavailable because this host was not started with a restartable Spring application. Use your local service manager.");
    return applicationStatus(journal.status());
  }

  @Override
  public synchronized Status request() {
    Status current = status();
    if (current.availability() == Availability.UNAVAILABLE || current.phase() == Phase.REQUESTED)
      return current;
    journal.request();
    try {
      endpoint.restart();
    } catch (RuntimeException failure) {
      journal.recordFailure();
      throw new IllegalStateException("Application restart failed. Check Zalava logs, then retry.");
    }
    return applicationStatus(journal.status());
  }

  private static Status applicationStatus(Status status) {
    String message =
        switch (status.phase()) {
          case IDLE ->
              "Ready to restart the Zalava application. This reloads installed modules without restarting the container or service.";
          case REQUESTED ->
              "Restart requested. Zalava will briefly disconnect. Sign in again when it is ready.";
          case COMPLETED ->
              "The Zalava application restarted. Installed modules can now be configured and started.";
          case FAILED -> "Application restart failed. Check Zalava logs, then retry.";
        };
    return new Status(status.availability(), status.phase(), status.requestedAt(), message);
  }

  /**
   * Keeps asynchronous failures observable while Spring Cloud owns the restart thread/lifecycle.
   */
  public static final class TrackedRestartEndpoint extends RestartEndpoint {
    private final FileSystemManagedZalavaRestart journal;

    public TrackedRestartEndpoint(FileSystemManagedZalavaRestart journal) {
      this.journal = journal;
    }

    @Override
    public synchronized ConfigurableApplicationContext doRestart() {
      try {
        ConfigurableApplicationContext result = super.doRestart();
        if (result == null) journal.recordFailure();
        return result;
      } catch (RuntimeException failure) {
        journal.recordFailure();
        throw failure;
      }
    }
  }
}
