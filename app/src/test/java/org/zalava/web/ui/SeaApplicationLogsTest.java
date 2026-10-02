package org.zalava.web.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SeaApplicationLogsTest {
  @TempDir Path directory;

  @Test
  void readsRecentLinesAcrossRotationAndRedactsCredentials() throws IOException {
    Path log = directory.resolve("sea.log");
    try (var archive =
        new GZIPOutputStream(Files.newOutputStream(directory.resolve("sea.log.2026-09-22.0.gz")))) {
      archive.write("before restart token=private-value\n".getBytes(StandardCharsets.UTF_8));
    }
    Files.writeString(log, "after restart Authorization: Bearer secret-value\n");

    SeaApplicationLogs.Snapshot snapshot = new SeaApplicationLogs(log.toString()).latest();

    assertThat(snapshot.available()).isTrue();
    assertThat(snapshot.lines())
        .containsExactly(
            "before restart token=[REDACTED]", "after restart Authorization: Bearer [REDACTED]");
  }

  @Test
  void missingLogIsUnavailable() {
    assertThat(
            new SeaApplicationLogs(directory.resolve("missing.log").toString())
                .latest()
                .available())
        .isFalse();
  }
}
