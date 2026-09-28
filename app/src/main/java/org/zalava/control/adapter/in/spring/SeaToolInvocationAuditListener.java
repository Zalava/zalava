package org.zalava.control.adapter.in.spring;

import java.time.Instant;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.zalava.control.application.InvocationLog;
import org.zalava.control.application.port.in.InvocationLogQueries.Entry;
import org.zalava.runtime.SeaToolInvocationAuditEvent;

@Component
public final class SeaToolInvocationAuditListener {
  private final InvocationLog log;

  public SeaToolInvocationAuditListener(InvocationLog log) {
    this.log = log;
  }

  @EventListener
  void record(SeaToolInvocationAuditEvent event) {
    log.record(
        new Entry(
            Instant.now().toString(),
            event.providerId(),
            event.toolName(),
            event.actorId(),
            event.confirmed(),
            event.classification(),
            event.policyTags(),
            event.sideEffecting(),
            event.success(),
            event.errorType(),
            event.errorMessage(),
            event.resultPreview(),
            event.durationMillis()));
  }
}
