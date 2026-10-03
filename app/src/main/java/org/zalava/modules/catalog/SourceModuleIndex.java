package org.zalava.modules.catalog;

import java.net.URI;
import java.util.List;
import java.util.Map;

public record SourceModuleIndex(int schemaVersion, List<Module> modules) {

  public SourceModuleIndex {
    modules = List.copyOf(modules);
  }

  public record Module(
      String moduleId,
      String version,
      String displayName,
      String description,
      URI supportUrl,
      Artifact artifact,
      Source source,
      Build build,
      Compatibility compatibility,
      Map<String, Object> configurationSchema,
      List<Factory> factories,
      List<Operation> operations,
      Security security) {

    public Module {
      configurationSchema = Map.copyOf(configurationSchema);
      factories = List.copyOf(factories);
      operations = List.copyOf(operations);
    }
  }

  public record Artifact(String groupId, String artifactId, String version) {}

  public record Source(URI repository, String license) {}

  public record Build(List<String> command, List<String> verificationCommand) {

    public Build {
      command = List.copyOf(command);
      verificationCommand = List.copyOf(verificationCommand);
    }
  }

  public record Compatibility(String zalavaRuntime) {}

  public record Factory(String factoryId, String providerType) {}

  public record Operation(
      String name, String description, boolean sideEffecting, Map<String, Object> inputSchema) {

    public Operation {
      inputSchema = Map.copyOf(inputSchema);
    }
  }

  public record Security(List<String> permissions) {

    public Security {
      permissions = List.copyOf(permissions);
    }
  }
}
