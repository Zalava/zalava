package org.zalava.modules.catalog.install.application.port.in;

import java.util.List;

public interface ModuleQueries {

  List<EnabledModule> enabledModules();

  record EnabledModule(
      String moduleId,
      String version,
      String sourceRepository,
      String binaryRepositoryId,
      List<String> declaredPermissions,
      String runtimeCompatibility) {

    public EnabledModule(
        String moduleId,
        String version,
        String sourceRepository,
        String binaryRepositoryId,
        List<String> declaredPermissions) {
      this(moduleId, version, sourceRepository, binaryRepositoryId, declaredPermissions, null);
    }

    public EnabledModule {
      declaredPermissions = List.copyOf(declaredPermissions);
    }
  }
}
