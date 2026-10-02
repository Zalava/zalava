package org.zalava.modules.catalog.install.application.port.in;

import java.util.List;

public interface ModuleQueries {

  List<EnabledModule> enabledModules();

  record EnabledModule(
      String moduleId,
      String version,
      String sourceRepository,
      String binaryRepositoryId,
      List<String> declaredPermissions) {

    public EnabledModule {
      declaredPermissions = List.copyOf(declaredPermissions);
    }
  }
}
