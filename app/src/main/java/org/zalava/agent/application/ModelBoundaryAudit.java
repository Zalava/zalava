package org.zalava.agent.application;

@FunctionalInterface
public interface ModelBoundaryAudit {
  void record(ModelBoundaryAuditEvent event);
}
