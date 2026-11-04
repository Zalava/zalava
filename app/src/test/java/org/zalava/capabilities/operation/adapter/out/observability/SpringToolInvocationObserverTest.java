package org.zalava.capabilities.operation.adapter.out.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.zalava.assistant.agent.application.ModelBoundary;
import org.zalava.capabilities.operation.application.port.out.ToolInvocationObservation;
import org.zalava.modules.runtime.ZalavaToolInvocationAuditEvent;
import org.zalava.platform.observability.application.port.out.OperationalMetrics;

class SpringToolInvocationObserverTest {
  @Test
  void boundsInputAndRedactsNestedCredentialsAndConfiguredSecrets() {
    var events = mock(ApplicationEventPublisher.class);
    var metrics = mock(OperationalMetrics.class);
    var observer =
        new SpringToolInvocationObserver(
            events, metrics, new ModelBoundary(2000, "configured-secret"));
    observer.observe(
        new ToolInvocationObservation(
            "clock",
            "read",
            "member",
            Map.of(),
            false,
            "zalava_backed",
            List.of(),
            false,
            true,
            null,
            null,
            null,
            1,
            Map.of(
                "zone",
                "UTC",
                "password",
                "private-password",
                "nested",
                Map.of("apiKey", "private-key"),
                "note",
                "configured-secret")));
    var event = ArgumentCaptor.forClass(ZalavaToolInvocationAuditEvent.class);
    verify(events).publishEvent(event.capture());
    String input = event.getValue().attributes().get("inputPreview");
    assertThat(input)
        .contains("UTC", "[REDACTED]")
        .doesNotContain("private-password", "private-key", "configured-secret");
    observer.observe(
        new ToolInvocationObservation(
            "clock",
            "read",
            "member",
            Map.of(),
            false,
            "zalava_backed",
            List.of(),
            false,
            true,
            null,
            null,
            null,
            1,
            Map.of("long", "x".repeat(2000))));
    verify(events, times(2)).publishEvent(event.capture());
    assertThat(event.getValue().attributes().get("inputPreview").length())
        .isLessThanOrEqualTo(1203);
  }
}
