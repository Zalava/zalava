package org.zalava.operation.adapter.out.observability;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.zalava.ZalavaOperationResult;
import org.zalava.observability.application.port.out.OperationalMetrics;
import org.zalava.operation.application.port.out.ToolInvocationObservation;
import org.zalava.operation.application.port.out.ToolInvocationObserver;
import org.zalava.runtime.SeaToolInvocationAuditEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public final class SpringToolInvocationObserver implements ToolInvocationObserver {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static final int AUDIT_PREVIEW_MAX_LENGTH = 1_200;

  private final ApplicationEventPublisher eventPublisher;
  private final OperationalMetrics metrics;

  public SpringToolInvocationObserver(
      ApplicationEventPublisher eventPublisher, OperationalMetrics metrics) {
    this.eventPublisher = eventPublisher;
    this.metrics = metrics;
  }

  @Override
  public void observe(ToolInvocationObservation observation) {
    try {
      eventPublisher.publishEvent(
          new SeaToolInvocationAuditEvent(
              observation.providerId(),
              observation.toolName(),
              observation.actorId(),
              observation.attributes(),
              observation.confirmed(),
              observation.classification(),
              observation.policyTags(),
              observation.sideEffecting(),
              observation.success(),
              observation.errorType(),
              truncate(observation.errorMessage()),
              resultPreview(observation.result()),
              observation.durationMillis()));
      metrics.toolInvocation(
          observation.providerId(),
          observation.toolName(),
          observation.success() ? "succeeded" : "failed",
          observation.durationMillis());
    } catch (RuntimeException ignored) {
      // Audit and metrics are optional; provider execution has already completed.
    }
  }

  private static String resultPreview(ZalavaOperationResult result) {
    if (result == null) {
      return null;
    }
    Map<String, Object> preview = new LinkedHashMap<>();
    preview.put("content", result.content());
    preview.put("metadata", result.metadata());
    try {
      return truncate(JSON.writeValueAsString(preview));
    } catch (JacksonException ex) {
      return truncate(String.valueOf(result.content()));
    }
  }

  private static String truncate(String value) {
    if (value == null || value.length() <= AUDIT_PREVIEW_MAX_LENGTH) {
      return value;
    }
    return value.substring(0, AUDIT_PREVIEW_MAX_LENGTH) + "...";
  }
}
