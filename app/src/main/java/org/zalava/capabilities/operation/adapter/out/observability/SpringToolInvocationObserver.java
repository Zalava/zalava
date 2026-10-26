package org.zalava.capabilities.operation.adapter.out.observability;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.zalava.api.ZalavaOperationResult;
import org.zalava.assistant.agent.application.ModelBoundary;
import org.zalava.capabilities.operation.application.port.out.ToolInvocationObservation;
import org.zalava.capabilities.operation.application.port.out.ToolInvocationObserver;
import org.zalava.modules.runtime.ZalavaToolInvocationAuditEvent;
import org.zalava.platform.observability.application.port.out.OperationalMetrics;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public final class SpringToolInvocationObserver implements ToolInvocationObserver {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static final int AUDIT_PREVIEW_MAX_LENGTH = 1_200;

  private final ApplicationEventPublisher eventPublisher;
  private final OperationalMetrics metrics;
  private final ModelBoundary boundary;

  public SpringToolInvocationObserver(
      ApplicationEventPublisher eventPublisher, OperationalMetrics metrics) {
    this(eventPublisher, metrics, new ModelBoundary(1200, ""));
  }

  @Autowired
  public SpringToolInvocationObserver(
      ApplicationEventPublisher eventPublisher,
      OperationalMetrics metrics,
      ModelBoundary boundary) {
    this.boundary = boundary;
    this.eventPublisher = eventPublisher;
    this.metrics = metrics;
  }

  @Override
  public void observe(ToolInvocationObservation observation) {
    try {
      eventPublisher.publishEvent(
          new ZalavaToolInvocationAuditEvent(
              observation.providerId(),
              observation.toolName(),
              observation.actorId(),
              attributes(observation),
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

  private Map<String, String> attributes(ToolInvocationObservation observation) {
    Map<String, String> attributes = new LinkedHashMap<>(observation.attributes());
    if (!observation.arguments().isEmpty()) {
      try {
        attributes.put(
            "inputPreview",
            truncate(boundary.redact(JSON.writeValueAsString(sanitize(observation.arguments())))));
      } catch (RuntimeException ignored) {
        attributes.put("inputPreview", "Input preview unavailable");
      }
    }
    return Map.copyOf(attributes);
  }

  private static Object sanitize(Object value) {
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> safe = new LinkedHashMap<>();
      map.forEach(
          (key, item) -> {
            String name = String.valueOf(key);
            safe.put(
                name,
                name.toLowerCase(Locale.ROOT)
                        .matches(".*(password|secret|token|authorization|api.?key|credential).*")
                    ? "[REDACTED]"
                    : sanitize(item));
          });
      return safe;
    }
    if (value instanceof List<?> list)
      return list.stream().map(SpringToolInvocationObserver::sanitize).toList();
    return value;
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
