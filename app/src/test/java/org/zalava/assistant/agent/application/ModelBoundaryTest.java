package org.zalava.assistant.agent.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModelBoundaryTest {

  @Test
  void redactsConfiguredSecretsBeforeModelInput() {
    ModelBoundary boundary = new ModelBoundary(100, "top-secret");

    assertThat(boundary.input("token=top-secret")).isEqualTo("token=[REDACTED]");
  }

  @Test
  void rejectsOverLimitInputWithoutPublishingContent() {
    ModelBoundary boundary = new ModelBoundary(4, "top-secret");

    assertThatThrownBy(() -> boundary.input("12345"))
        .isInstanceOf(ModelBoundaryViolation.class)
        .hasMessage("Model input exceeds configured character limit");
  }

  @Test
  void redactsModelOutputAndAuditPreviewValues() {
    ModelBoundary boundary = new ModelBoundary(100, "top-secret");

    assertThat(boundary.redact("result top-secret")).isEqualTo("result [REDACTED]");
  }

  @Test
  void recordsRedactedAllowAndDenyEvidenceWithoutContent() {
    List<ModelBoundaryAuditEvent> events = new ArrayList<>();
    ModelBoundary boundary = new ModelBoundary(4, "top-secret", events::add);

    boundary.input("safe");
    assertThatThrownBy(() -> boundary.input("12345")).isInstanceOf(ModelBoundaryViolation.class);

    assertThat(events)
        .containsExactly(
            new ModelBoundaryAuditEvent(ModelBoundaryAuditEvent.Decision.ALLOWED, 4, 4, 1),
            new ModelBoundaryAuditEvent(ModelBoundaryAuditEvent.Decision.DENIED, 5, 5, 1));
    assertThat(events.toString()).doesNotContain("top-secret", "safe", "12345");
  }
}
