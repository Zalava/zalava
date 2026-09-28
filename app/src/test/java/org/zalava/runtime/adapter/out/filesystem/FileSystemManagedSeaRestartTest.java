package org.zalava.runtime.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.zalava.runtime.application.port.in.ManagedSeaRestart;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileSystemManagedSeaRestartTest {
  @TempDir Path workspace;

  @Test
  void persistsRequestedRestartAndCompletesItOnTheNextSeaProcess() {
    Clock clock = Clock.fixed(Instant.parse("2026-09-26T18:00:00Z"), ZoneOffset.UTC);
    FileSystemManagedSeaRestart first = new FileSystemManagedSeaRestart(workspace, clock, true);

    assertThat(first.status().phase()).isEqualTo(ManagedSeaRestart.Phase.IDLE);
    assertThat(first.request().phase()).isEqualTo(ManagedSeaRestart.Phase.REQUESTED);
    assertThat(first.request().phase()).isEqualTo(ManagedSeaRestart.Phase.REQUESTED);

    FileSystemManagedSeaRestart restarted = new FileSystemManagedSeaRestart(workspace, clock, true);

    assertThat(restarted.status())
        .extracting(ManagedSeaRestart.Status::availability, ManagedSeaRestart.Status::phase)
        .containsExactly(
            ManagedSeaRestart.Availability.AVAILABLE, ManagedSeaRestart.Phase.COMPLETED);
  }

  @Test
  void reportsAHelpfulUnavailableStateWithoutAHostDispatcher() {
    FileSystemManagedSeaRestart restart =
        new FileSystemManagedSeaRestart(workspace, Clock.systemUTC(), false);

    assertThat(restart.status())
        .extracting(ManagedSeaRestart.Status::availability, ManagedSeaRestart.Status::phase)
        .containsExactly(ManagedSeaRestart.Availability.UNAVAILABLE, ManagedSeaRestart.Phase.IDLE);
    assertThat(restart.request().message()).contains("dispatcher is not installed");
  }

  @Test
  void reportsHostDispatcherFailureWithoutExposingItsOutput() throws Exception {
    FileSystemManagedSeaRestart restart =
        new FileSystemManagedSeaRestart(workspace, Clock.systemUTC(), true);
    restart.request();
    Files.writeString(workspace.resolve("sea-runtime-restart/failure"), "host command details");

    assertThat(restart.status())
        .extracting(ManagedSeaRestart.Status::phase, ManagedSeaRestart.Status::message)
        .containsExactly(
            ManagedSeaRestart.Phase.FAILED,
            "SEA restart failed. Check the SEA logs for service-manager diagnostics.");
  }
}
