package org.zalava.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AcceptanceTimingTest {
  @TempDir Path directory;

  @Test
  void preservesResultsAndRecordsFailuresIncludingAssertionErrors() throws Exception {
    assertThat(AcceptanceTiming.measure(directory, "download", () -> "artifact"))
        .isEqualTo("artifact");
    var failure = new AssertionError("acceptance failure");
    assertThatThrownBy(
            () ->
                AcceptanceTiming.measure(
                    directory,
                    "startup",
                    () -> {
                      throw failure;
                    }))
        .isSameAs(failure);
    var rows = Files.readAllLines(directory.resolve("timings.tsv"));
    assertThat(rows).hasSize(2);
    assertThat(rows.get(0)).matches("download\\tpassed\\t[0-9]+");
    assertThat(rows.get(1)).matches("startup\\tfailed\\t[0-9]+");
  }

  @Test
  void retainsConcurrentJourneyRecords() throws Exception {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> AcceptanceTiming.measure(directory, "docker", () -> 1));
      var second = executor.submit(() -> AcceptanceTiming.measure(directory, "compose", () -> 2));
      assertThat(first.get()).isEqualTo(1);
      assertThat(second.get()).isEqualTo(2);
    }
    assertThat(Files.readAllLines(directory.resolve("timings.tsv")))
        .hasSize(2)
        .allMatch(row -> row.matches("(docker|compose)\\tpassed\\t[0-9]+"));
  }

  @Test
  void unavailableDiagnosticsNeverReplaceTheOriginalOutcome() throws Exception {
    Path file = Files.createFile(directory.resolve("not-a-directory"));
    assertThat(AcceptanceTiming.measure(file, "success", () -> 42)).isEqualTo(42);
    var failure = new IllegalStateException("original failure");
    assertThatThrownBy(
            () ->
                AcceptanceTiming.measure(
                    file,
                    "failure",
                    () -> {
                      throw failure;
                    }))
        .isSameAs(failure);
  }
}
