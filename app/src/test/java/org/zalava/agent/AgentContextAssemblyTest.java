package org.zalava.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentContextAssemblyTest {

  @Test
  void normalizesNullPromptAndDefensivelyCopiesSourceMetrics() {
    List<AgentContextAssembly.SourceMetric> metrics = new ArrayList<>();
    metrics.add(new AgentContextAssembly.SourceMetric("memory", 12, 8));

    AgentContextAssembly assembly = new AgentContextAssembly(null, 1, 10, 8, metrics);
    metrics.clear();

    assertThat(assembly.prompt()).isEmpty();
    assertThat(assembly.sourceMetrics())
        .containsExactly(new AgentContextAssembly.SourceMetric("memory", 12, 8));
  }

  @Test
  void rejectsInconsistentAssemblyBoundsAndSourceCount() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new AgentContextAssembly("prompt", -1, 1, 0, List.of()))
        .withMessage("sourceCount must not be negative");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new AgentContextAssembly("prompt", 0, 1, 2, List.of()))
        .withMessage("charactersUsed must not exceed characterBudget");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new AgentContextAssembly("prompt", 1, 1, 0, List.of()))
        .withMessage("sourceCount must match sourceMetrics size");
  }

  @Test
  void rejectsInvalidSourceMetricBounds() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new AgentContextAssembly.SourceMetric(" ", 1, 0))
        .withMessage("sourceType must not be blank");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new AgentContextAssembly.SourceMetric("memory", 1, 2))
        .withMessage("charactersUsed must not exceed charactersAvailable");
  }
}
