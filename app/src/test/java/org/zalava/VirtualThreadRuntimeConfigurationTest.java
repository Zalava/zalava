package org.zalava;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.core.task.AsyncTaskExecutor;
import org.zalava.support.ZalavaComponentTest;

@ZalavaComponentTest
class VirtualThreadRuntimeConfigurationTest {

  @Autowired
  @Qualifier("applicationTaskExecutor")
  private AsyncTaskExecutor applicationTaskExecutor;

  @Autowired private Environment environment;

  @Test
  void runsApplicationTasksOnVirtualThreadsAndKeepsTheJvmAlive() throws Exception {
    Future<Boolean> virtualThread =
        applicationTaskExecutor.submit(() -> Thread.currentThread().isVirtual());

    assertThat(virtualThread.get()).isTrue();
    assertThat(environment.getProperty("spring.main.keep-alive", Boolean.class)).isTrue();
  }
}
