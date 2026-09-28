package org.zalava.speech;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.zalava.FactorySecretAccess;
import org.zalava.ProviderFactoryContext;
import org.zalava.SeaModule;
import org.zalava.catalog.install.adapter.out.filesystem.FileSystemModuleEnablement;
import org.zalava.catalog.install.application.port.out.ModuleEnablement;
import org.zalava.runtime.DefaultSeaRuntime;
import org.zalava.runtime.ExternalSeaModuleLoader;
import org.zalava.runtime.StaticSeaModuleRegistry;

/**
 * Loads the external speech fixture through the real {@link ExternalSeaModuleLoader} so tests
 * exercise the same scoped configuration, secret, and service-resolution path used at runtime.
 */
final class SpeechFixtureModule implements AutoCloseable {
  static final String MODULE_ID = "sea-external-module-fixture";
  static final SpeechAudioFormat FORMAT = new SpeechAudioFormat("wav", "pcm_s16le", 16000);
  static final SpeechAudioFormat UNSUPPORTED_FORMAT = new SpeechAudioFormat("wav", "opus", 48000);
  private static final String CREDENTIAL_REFERENCE = "fixture-token";

  private final ExternalSeaModuleLoader loader;
  private final DefaultSeaRuntime runtime;

  private SpeechFixtureModule(ExternalSeaModuleLoader loader, DefaultSeaRuntime runtime) {
    this.loader = loader;
    this.runtime = runtime;
  }

  static SpeechFixtureModule start(Path workspace, Map<String, Object> factoryConfiguration)
      throws Exception {
    return start(workspace, factoryConfiguration, defaultSecrets());
  }

  static SpeechFixtureModule start(
      Path workspace, Map<String, Object> factoryConfiguration, FactorySecretAccess moduleSecrets)
      throws Exception {
    Path artifact = installFixtureJar(workspace);
    FileSystemModuleEnablement registry = new FileSystemModuleEnablement(workspace);
    registry.enable(enabledModule(artifact));
    ExternalSeaModuleLoader loader = new ExternalSeaModuleLoader(registry);
    try {
      List<SeaModule> modules = loader.loadModules();
      ProviderFactoryContext context =
          new ProviderFactoryContext(
              Map.of(
                  "modules",
                  Map.of(MODULE_ID, Map.of("factories", Map.of("services", factoryConfiguration)))),
              FactorySecretAccess.none(),
              Map.of(MODULE_ID, moduleSecrets),
              Map.of());
      DefaultSeaRuntime runtime =
          new DefaultSeaRuntime(new StaticSeaModuleRegistry(modules), context);
      return new SpeechFixtureModule(loader, runtime);
    } catch (RuntimeException exception) {
      loader.close();
      throw exception;
    }
  }

  static FactorySecretAccess defaultSecrets() {
    return reference ->
        CREDENTIAL_REFERENCE.equals(reference)
            ? java.util.Optional.of("fixture-secret".toCharArray())
            : java.util.Optional.empty();
  }

  static Map<String, Object> configuration(Object... entries) {
    Map<String, Object> configuration = new java.util.HashMap<>();
    configuration.put("credentialRef", CREDENTIAL_REFERENCE);
    for (int index = 0; index < entries.length; index += 2) {
      configuration.put((String) entries[index], entries[index + 1]);
    }
    return configuration;
  }

  SpeechRecognition recognition() {
    return runtime.findService(SpeechRecognition.CONTRACT).orElseThrow().service();
  }

  SpeechSynthesis synthesis() {
    return runtime.findService(SpeechSynthesis.CONTRACT).orElseThrow().service();
  }

  @Override
  public void close() {
    try {
      runtime.close();
    } finally {
      try {
        loader.close();
      } catch (Exception exception) {
        throw new IllegalStateException("Unable to close the fixture loader", exception);
      }
    }
  }

  private static Path installFixtureJar(Path workspace) throws Exception {
    Path source = Path.of(System.getProperty("sea.test.external-module-jar"));
    Path artifact =
        workspace.resolve(
            "source-module-installation/modules/"
                + MODULE_ID
                + "/1.0.0/"
                + MODULE_ID
                + "-1.0.0.jar");
    Files.createDirectories(artifact.getParent());
    return Files.copy(source, artifact);
  }

  private static ModuleEnablement.EnabledModule enabledModule(Path artifact) throws Exception {
    return new ModuleEnablement.EnabledModule(
        MODULE_ID,
        "1.0.0",
        artifact.toString(),
        "sha256:"
            + HexFormat.of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(artifact))),
        ">=0.1.0");
  }
}
