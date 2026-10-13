package org.zalava.modules.runtime.adapter.out.actuator;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.cloud.context.config.ContextRefreshedWithApplicationEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.zalava.modules.runtime.adapter.out.actuator.ActuatorManagedZalavaRestart.TrackedRestartEndpoint;
import org.zalava.modules.runtime.adapter.out.filesystem.FileSystemManagedZalavaRestart;
import org.zalava.modules.runtime.application.port.in.ManagedZalavaRestart.Phase;

class TrackedRestartEndpointTest {
  @TempDir Path workspace;

  @Test
  void recordsUnavailableNativeRestartWithoutClaimingCompletion() {
    var journal = new FileSystemManagedZalavaRestart(workspace, Clock.systemUTC(), true);
    journal.request();
    var endpoint = new TrackedRestartEndpoint(journal);
    assertThat(endpoint.isRunning()).isFalse();
    assertThat(endpoint.doRestart()).isNull();
    assertThat(journal.status().phase()).isEqualTo(Phase.FAILED);
  }

  @Test
  void recordsFailedNativeStartupAndAllowsAnotherRequest() {
    var journal = new FileSystemManagedZalavaRestart(workspace, Clock.systemUTC(), true);
    journal.request();
    var endpoint = new TrackedRestartEndpoint(journal);
    try (var context = new AnnotationConfigApplicationContext()) {
      context.refresh();
      SpringApplication failing =
          new SpringApplication(TrackedRestartEndpointTest.class) {
            @Override
            public ConfigurableApplicationContext run(String... args) {
              throw new IllegalStateException("startup failure");
            }
          };
      endpoint.onApplicationEvent(
          new ContextRefreshedWithApplicationEvent(failing, new String[0], context));
      assertThat(endpoint.isRunning()).isTrue();
      assertThatThrownBy(endpoint::doRestart).isInstanceOf(IllegalStateException.class);
      assertThat(journal.status().phase()).isEqualTo(Phase.FAILED);
      assertThat(journal.status().message()).doesNotContain("startup failure");
      assertThat(journal.request().phase()).isEqualTo(Phase.REQUESTED);
    }
  }
}
