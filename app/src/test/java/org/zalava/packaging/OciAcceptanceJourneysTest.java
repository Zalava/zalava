package org.zalava.packaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.opentest4j.MultipleFailuresError;

class OciAcceptanceJourneysTest {
  @Test
  @Timeout(10)
  void startsBothJourneysConcurrentlyAndWaitsForBothBeforeImageCleanup() {
    var started = new CyclicBarrier(2);
    var completed = new AtomicInteger();

    OciAcceptanceJourneys.verify(
        () -> {
          started.await(5, TimeUnit.SECONDS);
          completed.incrementAndGet();
          return null;
        },
        () -> {
          started.await(5, TimeUnit.SECONDS);
          completed.incrementAndGet();
          return null;
        });

    assertThat(completed.get()).isEqualTo(2);
  }

  @Test
  @Timeout(10)
  void reportsBothFailuresInsteadOfSkippingTheOtherJourney() {
    var started = new CyclicBarrier(2);
    var completed = new AtomicInteger();

    var failure =
        assertThrows(
            MultipleFailuresError.class,
            () ->
                OciAcceptanceJourneys.verify(
                    () -> {
                      started.await(5, TimeUnit.SECONDS);
                      completed.incrementAndGet();
                      throw new IOException("Docker journey failed");
                    },
                    () -> {
                      started.await(5, TimeUnit.SECONDS);
                      completed.incrementAndGet();
                      throw new IOException("Compose journey failed");
                    }));

    assertThat(completed.get()).isEqualTo(2);
    assertThat(failure.getFailures()).hasSize(2);
    assertThat(failure.getFailures().get(0)).hasRootCauseMessage("Docker journey failed");
    assertThat(failure.getFailures().get(1)).hasRootCauseMessage("Compose journey failed");
  }
}
