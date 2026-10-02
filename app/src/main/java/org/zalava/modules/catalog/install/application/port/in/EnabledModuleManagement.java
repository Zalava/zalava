package org.zalava.modules.catalog.install.application.port.in;

/** Administrator lifecycle entry point for enabling state that does not go through an install. */
public interface EnabledModuleManagement {

  DisableOutcome disable(String moduleId);

  record DisableOutcome(String moduleId, boolean changed) {}
}
