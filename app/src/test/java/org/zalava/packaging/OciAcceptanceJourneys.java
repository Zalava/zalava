package org.zalava.packaging;

import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/** Independent runtime fixtures share only the immutable image, never their state. */
final class OciAcceptanceJourneys {
  private OciAcceptanceJourneys() {}

  static void verify(Callable<Void> docker, Callable<Void> compose) {
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var dockerResult = executor.submit(docker);
      var composeResult = executor.submit(compose);
      // Await both journeys before the caller removes the shared image, even on failure.
      assertAll(
          "Independent journeys against the same built image",
          dockerResult::get,
          composeResult::get);
    }
  }
}
