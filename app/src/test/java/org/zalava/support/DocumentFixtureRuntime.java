package org.zalava.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.zalava.api.FactorySecretAccess;
import org.zalava.api.ProviderFactoryContext;
import org.zalava.api.ZalavaModule;
import org.zalava.api.ZalavaServiceContract;
import org.zalava.api.extensions.content.ContentExtractor;
import org.zalava.modules.catalog.install.adapter.out.filesystem.FileSystemModuleEnablement;
import org.zalava.modules.catalog.install.application.port.out.ModuleEnablement;
import org.zalava.modules.runtime.DefaultZalavaRuntime;
import org.zalava.modules.runtime.ExternalZalavaModuleLoader;
import org.zalava.modules.runtime.LoadedZalavaProvider;
import org.zalava.modules.runtime.StaticZalavaModuleRegistry;
import org.zalava.modules.runtime.ZalavaRuntime;

/** Uses the independently built JAR, not module classes injected on the host classpath. */
public final class DocumentFixtureRuntime implements ZalavaRuntime {
  private final ExternalZalavaModuleLoader loader;
  private final DefaultZalavaRuntime delegate;
  public volatile boolean available = true;

  public DocumentFixtureRuntime() throws Exception {
    Path workspace = Files.createTempDirectory("zalava-attachment-extractor-");
    Path artifact =
        workspace.resolve(
            "source-module-installation/modules/zalava-external-module-fixture/1.0.0/zalava-external-module-fixture-1.0.0.jar");
    Files.createDirectories(artifact.getParent());
    Files.copy(Path.of(System.getProperty("zalava.test.external-module-jar")), artifact);
    String module = "zalava-external-module-fixture";
    var registry = new FileSystemModuleEnablement(workspace);
    registry.enable(
        new ModuleEnablement.EnabledModule(
            module,
            "1.0.0",
            artifact.toString(),
            "sha256:"
                + HexFormat.of()
                    .formatHex(
                        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(artifact))),
            ">=0.1.0"));
    loader = new ExternalZalavaModuleLoader(registry);
    ProviderFactoryContext context =
        new ProviderFactoryContext(
            Map.of(
                "modules",
                Map.of(
                    module,
                    Map.of(
                        "factories",
                        Map.of("services", Map.of("credentialRef", "fixture-token"))))),
            FactorySecretAccess.none(),
            Map.of(module, ref -> Optional.of("fixture-secret".toCharArray())),
            Map.of());
    delegate =
        new DefaultZalavaRuntime(new StaticZalavaModuleRegistry(loader.loadModules()), context);
    assertThat(delegate.findService(ContentExtractor.CONTRACT)).isPresent();
  }

  @Override
  public List<ZalavaModule> modules() {
    return delegate.modules();
  }

  @Override
  public List<ZalavaModule> activeModules() {
    return available ? delegate.modules() : List.of();
  }

  @Override
  public List<LoadedZalavaProvider> loadedProviders() {
    return delegate.loadedProviders();
  }

  @Override
  public <T> Optional<LoadedZalavaService<T>> findService(ZalavaServiceContract<T> contract) {
    return available ? delegate.findService(contract) : Optional.empty();
  }

  @Override
  public void close() {
    try {
      delegate.close();
    } finally {
      try {
        loader.close();
      } catch (IOException exception) {
        throw new IllegalStateException("Unable to close fixture artifact loader", exception);
      }
    }
  }
}
