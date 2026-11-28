package org.zalava.modules.catalog.application.port.in;

import org.zalava.api.ModuleConfigurationStatus;

/** Read-only configuration lifecycle status without configuration values or secrets. */
public interface ModuleConfigurationStatusQueries {
  ModuleConfigurationStatus status(String moduleId);
}
