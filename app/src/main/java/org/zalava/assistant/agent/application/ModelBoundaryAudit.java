package org.zalava.assistant.agent.application;

@FunctionalInterface
public interface ModelBoundaryAudit {
  void record(ModelBoundaryAuditEvent event);
}
