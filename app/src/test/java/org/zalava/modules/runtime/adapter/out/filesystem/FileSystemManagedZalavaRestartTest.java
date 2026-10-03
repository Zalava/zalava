package org.zalava.modules.runtime.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.modules.runtime.application.port.in.ManagedZalavaRestart;

class FileSystemManagedZalavaRestartTest {
  @TempDir Path workspace;

  @Test
  void persistsRequestedRestartAndCompletesItOnTheNextZalavaProcess() {
    Clock clock = Clock.fixed(Instant.parse("2026-09-26T18:00:00Z"), ZoneOffset.UTC);
    FileSystemManagedZalavaRestart first =
        new FileSystemManagedZalavaRestart(workspace, clock, true);

    assertThat(first.status().phase()).isEqualTo(ManagedZalavaRestart.Phase.IDLE);
    assertThat(first.request().phase()).isEqualTo(ManagedZalavaRestart.Phase.REQUESTED);
    assertThat(first.request().phase()).isEqualTo(ManagedZalavaRestart.Phase.REQUESTED);

    FileSystemManagedZalavaRestart restarted =
        new FileSystemManagedZalavaRestart(workspace, clock, true);

    assertThat(restarted.status())
        .extracting(ManagedZalavaRestart.Status::availability, ManagedZalavaRestart.Status::phase)
        .containsExactly(
            ManagedZalavaRestart.Availability.AVAILABLE, ManagedZalavaRestart.Phase.COMPLETED);
  }

  @Test
  void reportsAHelpfulUnavailableStateWithoutAHostDispatcher() {
    FileSystemManagedZalavaRestart restart =
        new FileSystemManagedZalavaRestart(workspace, Clock.systemUTC(), false);

    assertThat(restart.status())
        .extracting(ManagedZalavaRestart.Status::availability, ManagedZalavaRestart.Status::phase)
        .containsExactly(
            ManagedZalavaRestart.Availability.UNAVAILABLE, ManagedZalavaRestart.Phase.IDLE);
    assertThat(restart.request().message()).contains("dispatcher is not installed");
  }

  @Test
  void reportsHostDispatcherFailureWithoutExposingItsOutput() throws Exception {
    FileSystemManagedZalavaRestart restart =
        new FileSystemManagedZalavaRestart(workspace, Clock.systemUTC(), true);
    restart.request();
    Files.writeString(workspace.resolve("zalava-runtime-restart/failure"), "host command details");

    assertThat(restart.status())
        .extracting(ManagedZalavaRestart.Status::phase, ManagedZalavaRestart.Status::message)
        .containsExactly(
            ManagedZalavaRestart.Phase.FAILED,
            "Zalava restart failed. Check the Zalava logs for service-manager diagnostics.");
  }
}
