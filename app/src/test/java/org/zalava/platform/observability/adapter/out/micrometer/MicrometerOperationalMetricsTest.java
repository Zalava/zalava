package org.zalava.platform.observability.adapter.out.micrometer;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.zalava.platform.observability.application.port.out.OperationalMetrics;

class MicrometerOperationalMetricsTest {
  @Test
  void recordsOnlyBoundedSanitizedOperationalTags() {
    {
      var registry = new SimpleMeterRegistry();
      var metrics = new MicrometerOperationalMetrics(registry);
      metrics.agentRun("succeeded", 12, List.of(new OperationalMetrics.ContextUse("memory", 42)));
      metrics.toolInvocation("provider", "read", "succeeded", 8);

      assertThat(registry.get("sea.agent.runs").counter().count()).isEqualTo(1);
      assertThat(registry.get("sea.agent.context.characters").summary().totalAmount())
          .isEqualTo(42);
      assertThat(registry.get("sea.provider.tool.duration").timer().count()).isEqualTo(1);
      assertThat(registry.getMeters().toString())
          .doesNotContain("actor-1", "secret", "notes/a.txt");
    }
  }

  @Test
  void collapsesDynamicProviderCardinalityOverflowToOther() {
    {
      var registry = new SimpleMeterRegistry();
      var metrics = new MicrometerOperationalMetrics(registry);
      for (char suffix = 'a'; suffix <= 'u'; suffix++) {
        metrics.toolInvocation("provider" + suffix, "read", "succeeded", 0);
      }

      assertThat(registry.find("sea.provider.tool.invocations").tags("provider", "other").counter())
          .isNotNull();
    }
  }

  @Test
  void permitsAbsentRegistryForDisabledExport() {
    var metrics =
        new MicrometerOperationalMetrics((io.micrometer.core.instrument.MeterRegistry) null);
    metrics.knowledgeLifecycle("register", "succeeded");
    metrics.taskExecution("failed", "todo", 1);
  }
}
