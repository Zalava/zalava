package org.zalava.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Callable;

/** Best-effort phase diagnostics; timing failures must never replace acceptance failures. */
public final class AcceptanceTiming {
  private static final System.Logger LOGGER = System.getLogger(AcceptanceTiming.class.getName());

  private AcceptanceTiming() {}

  public static <T> T measure(Path diagnostics, String phase, Callable<T> action) throws Exception {
    long started = System.nanoTime();
    boolean passed = false;
    try {
      T result = action.call();
      passed = true;
      return result;
    } finally {
      record(diagnostics, phase, passed, System.nanoTime() - started);
    }
  }

  private static synchronized void record(
      Path diagnostics, String phase, boolean passed, long elapsedNanos) {
    try {
      Files.createDirectories(diagnostics);
      Files.writeString(
          diagnostics.resolve("timings.tsv"),
          phase + "\t" + (passed ? "passed" : "failed") + "\t" + elapsedNanos + "\n",
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException | SecurityException failure) {
      LOGGER.log(System.Logger.Level.WARNING, "Could not write acceptance timing", failure);
    }
  }
}
