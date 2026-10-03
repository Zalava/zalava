package org.zalava.modules.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.api.ModuleDescriptor;
import org.zalava.api.ProviderCapabilities;
import org.zalava.api.ProviderDescriptor;
import org.zalava.api.ProviderFactory;
import org.zalava.api.ProviderFactoryContext;
import org.zalava.api.ProviderFactoryDescriptor;
import org.zalava.api.ZalavaModule;
import org.zalava.api.ZalavaOperationResult;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.api.extensions.content.ContentExtractor;
import org.zalava.modules.catalog.install.SourceModuleInstallationException;
import org.zalava.modules.catalog.install.adapter.out.filesystem.FileSystemModuleEnablement;
import org.zalava.modules.catalog.install.application.port.out.ModuleEnablement;

class ExternalZalavaModuleLoaderTest {

  @TempDir Path workspace;

  @Test
  void discoversInstalledDescriptorsWithoutCreatingUnconfiguredProviders() throws Exception {
    Path artifact = installFixtureJar();
    FileSystemModuleEnablement registry = enableFixture(artifact);
    ProviderFactoryContext context =
        new ProviderFactoryContext(
            Map.of(
                "modules",
                Map.of(
                    "zalava-external-module-fixture",
                    Map.of(
                        "factories",
                        Map.of("external-fixture-factory", Map.of("rejectCreation", true))))));
    try (ExternalZalavaModuleLoader loader = new ExternalZalavaModuleLoader(registry, context)) {
      List<ZalavaModule> modules = loader.loadModules();
      assertThat(modules).hasSize(1);
      assertThatThrownBy(() -> loader.validateLoadedModules(registry.enabledModules(), modules))
          .isInstanceOf(ExternalZalavaModuleLoadingException.class)
          .hasMessageContaining("Fixture provider creation rejected");
      assertThatThrownBy(
              () -> new DefaultZalavaRuntime(new StaticZalavaModuleRegistry(modules), context))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Fixture provider creation rejected");
    }
  }

  @Test
  void loadsEnabledServiceModuleAndProviderFromExternalJar() throws Exception {
    Path artifact = installFixtureJar();
    FileSystemModuleEnablement registry = enableFixture(artifact);

    try (ExternalZalavaModuleLoader loader = new ExternalZalavaModuleLoader(registry)) {
      List<ZalavaModule> modules = loader.loadModules();
      DefaultZalavaRuntime runtime =
          new DefaultZalavaRuntime(
              new StaticZalavaModuleRegistry(modules),
              org.zalava.api.ProviderFactoryContext.empty());

      assertThat(modules)
          .extracting(module -> module.descriptor().moduleId())
          .containsExactly("zalava-external-module-fixture");
      assertThat(runtime.providers())
          .extracting(provider -> provider.descriptor().providerId())
          .containsExactly("external-fixture-provider");
      assertThat(modules.getFirst().serviceFactories())
          .extracting(factory -> factory.contract().serviceId())
          .containsExactlyInAnyOrder(
              ContentExtractor.CONTRACT.serviceId(),
              org.zalava.api.extensions.speech.SpeechRecognition.CONTRACT.serviceId(),
              org.zalava.api.extensions.speech.SpeechSynthesis.CONTRACT.serviceId());
    }
  }

  @Test
  void externalContentExtractorFixtureDoesNotBundleModuleApiClasses() throws Exception {
    Path artifact = installFixtureJar();

    try (var jar = new java.util.jar.JarFile(artifact.toFile())) {
      assertThat(jar.stream().map(java.util.jar.JarEntry::getName))
          .noneMatch(name -> name.startsWith("org/zalava/content/"));
    }
  }

  @Test
  void validatesExternalFactoriesWithOnlyTheirScopedConfiguration() throws Exception {
    AtomicReference<ProviderFactoryContext> received = new AtomicReference<>();
    ProviderFactory factory =
        new ProviderFactory() {
          @Override
          public ProviderFactoryDescriptor descriptor() {
            return validFactoryDescriptor("test-module");
          }

          @Override
          public List<ZalavaProvider> createProviders(ProviderFactoryContext context) {
            received.set(context);
            return List.of(new TestProvider(validProviderDescriptor("test-module")));
          }
        };
    ProviderFactoryContext context =
        new ProviderFactoryContext(
            Map.of(
                "modules",
                Map.of(
                    "test-module",
                        Map.of(
                            "factories", Map.of("factory", Map.of("credentialRef", "brave-key"))),
                    "other-module",
                        Map.of("factories", Map.of("other", Map.of("mustNotLeak", true))))));

    try (ExternalZalavaModuleLoader loader = new ExternalZalavaModuleLoader(List::of, context)) {
      loader.validateLoadedModules(
          List.of(enabledModule("test-module", "1.0.0")),
          List.of(new TestModule(validModuleDescriptor("test-module"), List.of(factory))));
    }

    assertThat(received.get().configuration())
        .containsExactly(Map.entry("credentialRef", "brave-key"));
  }

  @Test
  void usesTheSameFactoryScopedConfigurationForValidationAndRuntimeCreation() throws Exception {
    List<Map<String, Object>> receivedConfigurations = new ArrayList<>();
    ProviderFactory factory =
        new ProviderFactory() {
          @Override
          public ProviderFactoryDescriptor descriptor() {
            return validFactoryDescriptor("test-module");
          }

          @Override
          public List<ZalavaProvider> createProviders(ProviderFactoryContext context) {
            receivedConfigurations.add(context.configuration());
            return List.of(new TestProvider(validProviderDescriptor("test-module")));
          }
        };
    ZalavaModule module = new TestModule(validModuleDescriptor("test-module"), List.of(factory));
    ProviderFactoryContext context =
        new ProviderFactoryContext(
            Map.of(
                "modules",
                Map.of(
                    "test-module",
                        Map.of(
                            "factories", Map.of("factory", Map.of("credentialRef", "brave-key"))),
                    "other-module",
                        Map.of("factories", Map.of("other", Map.of("mustNotLeak", true))))));

    try (ExternalZalavaModuleLoader loader = new ExternalZalavaModuleLoader(List::of, context)) {
      loader.validateLoadedModules(List.of(enabledModule("test-module", "1.0.0")), List.of(module));
    }
    ZalavaRuntime runtime =
        new DefaultZalavaRuntime(new StaticZalavaModuleRegistry(List.of(module)), context);

    assertThat(receivedConfigurations)
        .containsExactly(
            Map.of("credentialRef", "brave-key"), Map.of("credentialRef", "brave-key"));

    runtime.close();
  }

  @Test
  void closesProvidersCreatedOnlyForExternalValidation() throws Exception {
    AtomicInteger closeCount = new AtomicInteger();
    ProviderFactory factory =
        new TestProviderFactory(
            validFactoryDescriptor("test-module"),
            List.of(
                new TestProvider(validProviderDescriptor("test-module")) {
                  @Override
                  public void close() {
                    closeCount.incrementAndGet();
                  }
                }));

    try (ExternalZalavaModuleLoader loader = new ExternalZalavaModuleLoader(List::of)) {
      loader.validateLoadedModules(
          List.of(enabledModule("test-module", "1.0.0")),
          List.of(new TestModule(validModuleDescriptor("test-module"), List.of(factory))));
    }

    assertThat(closeCount).hasValue(1);
  }

  @Test
  void rejectsEnabledArtifactChangedAfterEnablement() throws Exception {
    Path artifact = installFixtureJar();
    FileSystemModuleEnablement registry = enableFixture(artifact);
    Files.writeString(artifact, "tampered");

    try (ExternalZalavaModuleLoader loader = new ExternalZalavaModuleLoader(registry)) {
      assertThatThrownBy(loader::loadModules)
          .isInstanceOf(SourceModuleInstallationException.class)
          .hasMessageContaining("digest does not match");
    }
  }

  @Test
  void skipsArtifactWithoutDeclaredZalavaModuleService() throws Exception {
    Path artifact =
        workspace.resolve(
            "source-module-installation/modules/empty-module/1.0.0/empty-module-1.0.0.jar");
    Files.createDirectories(artifact.getParent());
    try (var output = new java.util.jar.JarOutputStream(Files.newOutputStream(artifact))) {
      // An empty valid JAR has no ZalavaModule service declaration.
    }
    FileSystemModuleEnablement registry = new FileSystemModuleEnablement(workspace);
    registry.enable(enabledModule("empty-module", artifact));

    try (ExternalZalavaModuleLoader loader = new ExternalZalavaModuleLoader(registry)) {
      assertThat(loader.loadModules()).isEmpty();
    }
  }

  @Test
  void loadsHealthyArtifactsWhenAnotherEnabledArtifactFailsValidation() throws Exception {
    Path artifact = installFixtureJar();
    FileSystemModuleEnablement registry = enableFixture(artifact);
    registry.enable(enabledModule("broken-module", artifact));

    try (ExternalZalavaModuleLoader loader = new ExternalZalavaModuleLoader(registry)) {
      assertThat(loader.loadModules())
          .extracting(module -> module.descriptor().moduleId())
          .containsExactly("zalava-external-module-fixture");
    }
  }

  @Test
  void loadsPrimaryAndRuntimeArtifactsInOneIsolatedClassLoaderAndCanRestart() throws Exception {
    Path primary = installFixtureJar();
    Path runtime = jarArtifact("runtime.jar", "example/runtime/Marker.class");
    FileSystemModuleEnablement registry = new FileSystemModuleEnablement(workspace);
    registry.enable(bundleEnabledModule("zalava-external-module-fixture", primary, runtime));

    try (ExternalZalavaModuleLoader loader = new ExternalZalavaModuleLoader(registry)) {
      List<ZalavaModule> modules = loader.loadModules();

      assertThat(modules).hasSize(1);
      assertThat(((URLClassLoader) modules.getFirst().getClass().getClassLoader()).getURLs())
          .containsExactly(primary.toUri().toURL(), runtime.toUri().toURL());
    }

    try (ExternalZalavaModuleLoader restarted = new ExternalZalavaModuleLoader(registry)) {
      assertThat(restarted.loadModules())
          .extracting(module -> module.descriptor().moduleId())
          .containsExactly("zalava-external-module-fixture");
    }
  }

  @Test
  void isolatesMissingRuntimeArtifactWithoutLoadingTheBundle() throws Exception {
    Path primary = installFixtureJar();
    ModuleEnablement.EnabledModule missingRuntime =
        new ModuleEnablement.EnabledModule(
            "zalava-external-module-fixture",
            "1.0.0",
            primary.toString(),
            digest(primary),
            ">=0.1.0",
            null,
            null,
            null,
            List.of(),
            List.of(
                new ModuleEnablement.RuntimeArtifact(
                    workspace.resolve("missing.jar").toString(), "sha256:" + "0".repeat(64))));

    try (ExternalZalavaModuleLoader loader =
        new ExternalZalavaModuleLoader(() -> List.of(missingRuntime))) {
      assertThat(loader.loadModules()).isEmpty();
    }
  }

  @Test
  void rejectsForbiddenHostApiPackageBeforeServiceDiscovery() throws Exception {
    Path forbidden = jarArtifact("forbidden.jar", "org/zalava/runtime/Injected.class");
    FileSystemModuleEnablement registry = new FileSystemModuleEnablement(workspace);
    registry.enable(enabledModule("forbidden-module", forbidden));

    try (ExternalZalavaModuleLoader loader = new ExternalZalavaModuleLoader(registry)) {
      assertThat(loader.loadModules()).isEmpty();
    }
  }

  @Test
  void rejectsTheRootModuleApiPackageBeforeServiceDiscovery() throws Exception {
    Path forbidden = jarArtifact("forbidden-api.jar", "org/zalava/Injected.class");
    FileSystemModuleEnablement registry = new FileSystemModuleEnablement(workspace);
    registry.enable(enabledModule("forbidden-api-module", forbidden));

    try (ExternalZalavaModuleLoader loader = new ExternalZalavaModuleLoader(registry)) {
      assertThat(loader.loadModules()).isEmpty();
    }
  }

  @Test
  void rejectsDuplicateArtifactPathsInAnEnabledBundle() throws Exception {
    Path primary = installFixtureJar();
    ModuleEnablement.EnabledModule duplicatePaths =
        bundleEnabledModule("duplicate-module", primary, primary);

    try (ExternalZalavaModuleLoader loader =
        new ExternalZalavaModuleLoader(() -> List.of(duplicatePaths))) {
      assertThat(loader.loadModules()).isEmpty();
    }
  }

  @Test
  void rejectsUnreadablePrimaryArtifactAndIgnoresDefaultPackageClasses() throws Exception {
    ModuleEnablement.EnabledModule missingPrimary =
        new ModuleEnablement.EnabledModule(
            "missing-primary",
            "1.0.0",
            workspace.resolve("missing-primary.jar").toString(),
            "sha256:" + "0".repeat(64),
            ">=0.1.0");
    ModuleEnablement.EnabledModule blankPrimary =
        new ModuleEnablement.EnabledModule(
            "blank-primary", "1.0.0", " ", "sha256:" + "0".repeat(64), ">=0.1.0");
    Path defaultPackage = jarArtifact("default-package.jar", "Marker.class");
    FileSystemModuleEnablement registry = new FileSystemModuleEnablement(workspace);
    registry.enable(enabledModule("default-package", defaultPackage));

    try (ExternalZalavaModuleLoader loader =
        new ExternalZalavaModuleLoader(() -> List.of(missingPrimary, blankPrimary))) {
      assertThat(loader.loadModules()).isEmpty();
    }
    try (ExternalZalavaModuleLoader loader = new ExternalZalavaModuleLoader(registry)) {
      assertThat(loader.loadModules()).isEmpty();
    }
  }

  @Test
  void rejectsAnArtifactThatCannotBeInspectedAsAJar() throws Exception {
    Path malformed =
        workspace.resolve("source-module-installation/modules/test-artifacts/1.0.0/malformed.jar");
    Files.createDirectories(malformed.getParent());
    Files.writeString(malformed, "not a jar");
    FileSystemModuleEnablement registry = new FileSystemModuleEnablement(workspace);
    registry.enable(enabledModule("malformed-module", malformed));

    try (ExternalZalavaModuleLoader loader = new ExternalZalavaModuleLoader(registry)) {
      assertThat(loader.loadModules()).isEmpty();
    }
  }

  @Test
  void rejectsPackageClaimedByAnotherEnabledBundleWhileKeepingHealthyModule() throws Exception {
    Path primary = installFixtureJar();
    Path colliding = jarArtifact("colliding.jar", "org/zalava/fixture/Conflict.class");
    FileSystemModuleEnablement registry = enableFixture(primary);
    registry.enable(enabledModule("zz-colliding-module", colliding));

    try (ExternalZalavaModuleLoader loader = new ExternalZalavaModuleLoader(registry)) {
      assertThat(loader.loadModules())
          .extracting(module -> module.descriptor().moduleId())
          .containsExactly("zalava-external-module-fixture");
    }
  }

  @Test
  void rejectsBlankExternalModuleDescriptorFields() throws Exception {
    try (ExternalZalavaModuleLoader loader = new ExternalZalavaModuleLoader(List::of)) {
      assertThatThrownBy(
              () ->
                  loader.validateLoadedModules(
                      List.of(enabledModule("blank-module", "1.0.0")),
                      List.of(
                          new TestModule(
                              new ModuleDescriptor("blank-module", "1.0.0", " ", "description")))))
          .isInstanceOf(ExternalZalavaModuleLoadingException.class)
          .hasMessage("External Zalava module blank-module display name must not be blank");
    }
  }

  @Test
  void rejectsProviderFactoryBelongingToAnotherModule() throws Exception {
    ProviderFactory factory =
        new TestProviderFactory(
            new ProviderFactoryDescriptor(
                "factory", "other-module", "test", "Factory", "Factory description"),
            List.of());

    try (ExternalZalavaModuleLoader loader = new ExternalZalavaModuleLoader(List::of)) {
      assertThatThrownBy(
              () ->
                  loader.validateLoadedModules(
                      List.of(enabledModule("test-module", "1.0.0")),
                      List.of(
                          new TestModule(validModuleDescriptor("test-module"), List.of(factory)))))
          .isInstanceOf(ExternalZalavaModuleLoadingException.class)
          .hasMessage(
              "External provider factory factory belongs to module other-module "
                  + "but owning module is test-module");
    }
  }

  @Test
  void rejectsProviderBelongingToAnotherModule() throws Exception {
    ZalavaProvider provider =
        new TestProvider(
            new ProviderDescriptor(
                "provider",
                "other-module",
                "test",
                "Provider",
                "Provider description",
                "1.0.0",
                ProviderCapabilities.toolsOnly(),
                List.of(),
                Map.of()));
    ProviderFactory factory =
        new TestProviderFactory(validFactoryDescriptor("test-module"), List.of(provider));

    try (ExternalZalavaModuleLoader loader = new ExternalZalavaModuleLoader(List::of)) {
      assertThatThrownBy(
              () ->
                  loader.validateLoadedModules(
                      List.of(enabledModule("test-module", "1.0.0")),
                      List.of(
                          new TestModule(validModuleDescriptor("test-module"), List.of(factory)))))
          .isInstanceOf(ExternalZalavaModuleLoadingException.class)
          .hasMessage(
              "External provider provider belongs to module other-module "
                  + "but owning module is test-module");
    }
  }

  @Test
  void rejectsProviderWithoutCapabilities() throws Exception {
    ZalavaProvider provider =
        new TestProvider(
            new ProviderDescriptor(
                "provider",
                "test-module",
                "test",
                "Provider",
                "Provider description",
                "1.0.0",
                null,
                List.of(),
                Map.of()));
    ProviderFactory factory =
        new TestProviderFactory(validFactoryDescriptor("test-module"), List.of(provider));

    try (ExternalZalavaModuleLoader loader = new ExternalZalavaModuleLoader(List::of)) {
      assertThatThrownBy(
              () ->
                  loader.validateLoadedModules(
                      List.of(enabledModule("test-module", "1.0.0")),
                      List.of(
                          new TestModule(validModuleDescriptor("test-module"), List.of(factory)))))
          .isInstanceOf(ExternalZalavaModuleLoadingException.class)
          .hasMessage("External provider provider capabilities must not be null");
    }
  }

  @Test
  void acceptsCompatibleRuntimeRangeAndLegacyMissingCompatibility() throws Exception {
    ProviderFactory factory =
        new TestProviderFactory(
            validFactoryDescriptor("test-module"),
            List.of(new TestProvider(validProviderDescriptor("test-module"))));

    try (ExternalZalavaModuleLoader loader = new ExternalZalavaModuleLoader(List::of)) {
      loader.validateLoadedModules(
          List.of(
              enabledModule("test-module", "1.0.0", ">=1.0.0 <2.0.0"),
              enabledModule("legacy-module", "1.0.0", null)),
          List.of(
              new TestModule(validModuleDescriptor("test-module"), List.of(factory)),
              new TestModule(validModuleDescriptor("legacy-module"))));
    }
  }

  @Test
  void rejectsIncompatibleRuntimeRange() throws Exception {
    try (ExternalZalavaModuleLoader loader = new ExternalZalavaModuleLoader(List::of)) {
      assertThatThrownBy(
              () ->
                  loader.validateLoadedModules(
                      List.of(enabledModule("test-module", "1.0.0", ">=2.0.0")),
                      List.of(new TestModule(validModuleDescriptor("test-module")))))
          .isInstanceOf(ExternalZalavaModuleLoadingException.class)
          .hasMessage(
              "External module test-module requires Zalava runtime >=2.0.0 "
                  + "but current runtime is 1.0.0");
    }
  }

  @Test
  void rejectsRuntimeRangeWhoseExclusiveUpperBoundExcludesCurrentRuntime() throws Exception {
    try (ExternalZalavaModuleLoader loader = new ExternalZalavaModuleLoader(List::of)) {
      assertThatThrownBy(
              () ->
                  loader.validateLoadedModules(
                      List.of(enabledModule("test-module", "1.0.0", ">=0.1.0 <1.0.0")),
                      List.of(new TestModule(validModuleDescriptor("test-module")))))
          .isInstanceOf(ExternalZalavaModuleLoadingException.class)
          .hasMessage(
              "External module test-module does not support Zalava runtime 1.0.0 "
                  + "(requires >=0.1.0 <1.0.0)");
    }
  }

  @Test
  void rejectsMalformedRuntimeRange() throws Exception {
    try (ExternalZalavaModuleLoader loader = new ExternalZalavaModuleLoader(List::of)) {
      assertThatThrownBy(
              () ->
                  loader.validateLoadedModules(
                      List.of(enabledModule("test-module", "1.0.0", ">=next")),
                      List.of(new TestModule(validModuleDescriptor("test-module")))))
          .isInstanceOf(ExternalZalavaModuleLoadingException.class)
          .hasMessage(
              "External module test-module zalavaRuntime compatibility is invalid: "
                  + "version must use major.minor.patch format");
    }
  }

  @Test
  void rejectsRuntimeRangeWithAnInvalidBoundOrder() throws Exception {
    try (ExternalZalavaModuleLoader loader = new ExternalZalavaModuleLoader(List::of)) {
      assertThatThrownBy(
              () ->
                  loader.validateLoadedModules(
                      List.of(enabledModule("test-module", "1.0.0", ">=2.0.0 <2.0.0")),
                      List.of(new TestModule(validModuleDescriptor("test-module")))))
          .isInstanceOf(ExternalZalavaModuleLoadingException.class)
          .hasMessage(
              "External module test-module zalavaRuntime compatibility is invalid: "
                  + "minimum must be lower than upper bound");
    }
  }

  @Test
  void rejectsProviderFactoryThatCannotCreateProviders() throws Exception {
    ProviderFactory factory = new FailingProviderFactory(validFactoryDescriptor("test-module"));

    try (ExternalZalavaModuleLoader loader = new ExternalZalavaModuleLoader(List::of)) {
      assertThatThrownBy(
              () ->
                  loader.validateLoadedModules(
                      List.of(enabledModule("test-module", "1.0.0")),
                      List.of(
                          new TestModule(validModuleDescriptor("test-module"), List.of(factory)))))
          .isInstanceOf(ExternalZalavaModuleLoadingException.class)
          .hasMessage("External provider factory factory failed to create providers: unavailable");
    }
  }

  private Path installFixtureJar() throws Exception {
    Path source = Path.of(System.getProperty("zalava.test.external-module-jar"));
    Path artifact =
        workspace.resolve(
            "source-module-installation/modules/zalava-external-module-fixture/1.0.0/"
                + "zalava-external-module-fixture-1.0.0.jar");
    Files.createDirectories(artifact.getParent());
    return Files.copy(source, artifact);
  }

  private FileSystemModuleEnablement enableFixture(Path artifact) throws Exception {
    FileSystemModuleEnablement registry = new FileSystemModuleEnablement(workspace);
    registry.enable(enabledModule("zalava-external-module-fixture", artifact));
    return registry;
  }

  private Path jarArtifact(String name, String... entries) throws Exception {
    Path artifact =
        workspace.resolve("source-module-installation/modules/test-artifacts/1.0.0").resolve(name);
    Files.createDirectories(artifact.getParent());
    try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(artifact))) {
      for (String entry : entries) {
        output.putNextEntry(new JarEntry(entry));
        output.write(new byte[] {0});
        output.closeEntry();
      }
    }
    return artifact;
  }

  private static ModuleEnablement.EnabledModule bundleEnabledModule(
      String moduleId, Path primary, Path runtime) throws Exception {
    return new ModuleEnablement.EnabledModule(
        moduleId,
        "1.0.0",
        primary.toString(),
        digest(primary),
        ">=0.1.0",
        null,
        null,
        null,
        List.of(),
        List.of(new ModuleEnablement.RuntimeArtifact(runtime.toString(), digest(runtime))));
  }

  private static String digest(Path artifact) throws Exception {
    return "sha256:"
        + HexFormat.of()
            .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(artifact)));
  }

  private static ModuleEnablement.EnabledModule enabledModule(String moduleId, Path artifact)
      throws Exception {
    return new ModuleEnablement.EnabledModule(
        moduleId,
        "1.0.0",
        artifact.toString(),
        "sha256:"
            + HexFormat.of()
                .formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(artifact))),
        ">=0.1.0");
  }

  private static ModuleEnablement.EnabledModule enabledModule(String moduleId, String version) {
    return enabledModule(moduleId, version, ">=0.1.0");
  }

  private static ModuleEnablement.EnabledModule enabledModule(
      String moduleId, String version, String compatibility) {
    return new ModuleEnablement.EnabledModule(
        moduleId, version, "unused.jar", "sha256:test", compatibility);
  }

  private static ModuleDescriptor validModuleDescriptor(String moduleId) {
    return new ModuleDescriptor(moduleId, "1.0.0", "Test Module", "Test module description");
  }

  private static ProviderFactoryDescriptor validFactoryDescriptor(String moduleId) {
    return new ProviderFactoryDescriptor(
        "factory", moduleId, "test", "Factory", "Factory description");
  }

  private static ProviderDescriptor validProviderDescriptor(String moduleId) {
    return new ProviderDescriptor(
        "provider",
        moduleId,
        "test",
        "Provider",
        "Provider description",
        "1.0.0",
        ProviderCapabilities.toolsOnly(),
        List.of(),
        Map.of());
  }

  private record TestModule(ModuleDescriptor descriptor, List<ProviderFactory> providerFactories)
      implements ZalavaModule {

    private TestModule(ModuleDescriptor descriptor) {
      this(descriptor, List.of());
    }
  }

  private record TestProviderFactory(
      ProviderFactoryDescriptor descriptor, List<ZalavaProvider> providers)
      implements ProviderFactory {

    @Override
    public List<ZalavaProvider> createProviders(ProviderFactoryContext context) {
      return providers;
    }
  }

  private record FailingProviderFactory(ProviderFactoryDescriptor descriptor)
      implements ProviderFactory {

    @Override
    public List<ZalavaProvider> createProviders(ProviderFactoryContext context) {
      throw new IllegalStateException("unavailable");
    }
  }

  private static class TestProvider implements ZalavaProvider {

    private final ProviderDescriptor descriptor;

    private TestProvider(ProviderDescriptor descriptor) {
      this.descriptor = descriptor;
    }

    @Override
    public ProviderDescriptor descriptor() {
      return descriptor;
    }

    @Override
    public ProviderCapabilities capabilities() {
      return descriptor.capabilities();
    }

    @Override
    public List<ZalavaToolDescriptor> listTools() {
      return List.of();
    }

    @Override
    public ZalavaOperationResult callTool(
        String toolName,
        java.util.Map<String, Object> argumentValues,
        org.zalava.api.InvocationContext context) {
      tools.jackson.databind.JsonNode arguments =
          new tools.jackson.databind.json.JsonMapper().valueToTree(argumentValues);
      return ZalavaOperationResult.success(Map.of());
    }
  }
}
