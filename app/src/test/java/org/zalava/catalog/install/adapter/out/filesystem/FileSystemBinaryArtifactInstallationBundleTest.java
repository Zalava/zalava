package org.zalava.catalog.install.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.catalog.SourceModuleIndex;
import org.zalava.catalog.install.SourceModuleInstallationException;
import org.zalava.catalog.install.application.port.out.BinaryArtifactInstallation.BundleInstall;
import org.zalava.catalog.install.application.port.out.BinaryArtifactInstallation.Install;
import org.zalava.catalog.install.application.port.out.BinaryArtifactInstallation.InstalledArtifact;
import org.zalava.catalog.install.application.port.out.BinaryArtifactInstallation.InstalledBundle;

/**
 * Covers the bundle installation paths of {@link FileSystemBinaryArtifactInstallation}: the
 * manifest-bearing bundle jar, the multi-jar {@link BundleInstall}, and managed-bundle discard. All
 * fixtures are built in-memory or in a temporary directory; no network or credentials.
 */
class FileSystemBinaryArtifactInstallationBundleTest {

  private static final String MANIFEST_ENTRY = "META-INF/sea-module-bundle.yaml";

  @TempDir Path workspace;

  // --- bundle jar with manifest ---

  @Test
  void installsDeclaredBundleMembersIntoTheManagedVersionDirectory() throws Exception {
    byte[] moduleJar = "module-bytes".getBytes();
    byte[] runtimeJar = "runtime-bytes".getBytes();
    String manifest =
        manifestWithPaths("module.jar", List.of("lib/impl.jar"), moduleJar, runtimeJar);
    Path bundle =
        bundleJar(manifest, entry("module.jar", moduleJar), entry("lib/impl.jar", runtimeJar));
    var installation = new FileSystemBinaryArtifactInstallation(workspace);

    InstalledBundle installed = installation.installBundle(install(bundle));

    assertThat(installed.artifacts())
        .hasSize(2)
        .satisfiesExactly(
            artifact -> {
              assertThat(artifact.path()).contains("bundle-module", "1.0.0", "module.jar");
              assertThat(artifact.digest()).isEqualTo("sha256:" + hex(moduleJar));
            },
            artifact -> {
              assertThat(artifact.path()).contains("bundle-module", "1.0.0", "lib", "impl.jar");
              assertThat(artifact.digest()).isEqualTo("sha256:" + hex(runtimeJar));
            });
    Path versionDirectory =
        workspace.resolve("source-module-installation/modules/bundle-module/1.0.0");
    assertThat(versionDirectory.resolve("module.jar")).isRegularFile();
    assertThat(versionDirectory.resolve("lib/impl.jar")).isRegularFile();
    try (Stream<Path> leftovers = Files.list(versionDirectory.getParent())) {
      assertThat(leftovers.filter(path -> path.getFileName().toString().contains("staging")))
          .isEmpty();
    }
  }

  @Test
  void requiresTheBundleManifestEntry() throws Exception {
    Path bundle = bundleJar(null, entry("module.jar", "module-bytes".getBytes()));
    var installation = new FileSystemBinaryArtifactInstallation(workspace);

    assertThatThrownBy(() -> installation.installBundle(install(bundle)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("manifest is required");
  }

  @Test
  void rejectsAManifestThatIsNotAnObject() throws Exception {
    Path bundle = bundleJar("[]", entry("module.jar", "module-bytes".getBytes()));
    var installation = new FileSystemBinaryArtifactInstallation(workspace);

    assertThatThrownBy(() -> installation.installBundle(install(bundle)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("manifest must be an object");
  }

  @Test
  void rejectsUnparsableManifestYaml() throws Exception {
    Path bundle = bundleJar("module: [unclosed", entry("module.jar", "module-bytes".getBytes()));
    var installation = new FileSystemBinaryArtifactInstallation(workspace);

    assertThatThrownBy(() -> installation.installBundle(install(bundle)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("Unable to read binary artifact bundle manifest");
  }

  @Test
  void rejectsNonObjectModuleEntry() throws Exception {
    Path bundle = bundleJar("module: not-a-map", entry("module.jar", "module-bytes".getBytes()));
    var installation = new FileSystemBinaryArtifactInstallation(workspace);

    assertThatThrownBy(() -> installation.installBundle(install(bundle)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("module must be an object");
  }

  @Test
  void rejectsRuntimeSectionThatIsNotAList() throws Exception {
    byte[] moduleJar = "module-bytes".getBytes();
    String manifest =
        "module:\n  path: module.jar\n  sha256: %s\nruntime: not-a-list".formatted(hex(moduleJar));
    Path bundle = bundleJar(manifest, entry("module.jar", moduleJar));
    var installation = new FileSystemBinaryArtifactInstallation(workspace);

    assertThatThrownBy(() -> installation.installBundle(install(bundle)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("runtime must be a list");
  }

  @Test
  void rejectsNonObjectRuntimeEntry() throws Exception {
    byte[] moduleJar = "module-bytes".getBytes();
    String manifest =
        """
        module:
          path: module.jar
          sha256: %s
        runtime:
          - not-a-map
        """
            .formatted(hex(moduleJar));
    Path bundle = bundleJar(manifest, entry("module.jar", moduleJar));
    var installation = new FileSystemBinaryArtifactInstallation(workspace);

    assertThatThrownBy(() -> installation.installBundle(install(bundle)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("runtime[0] must be an object");
  }

  @Test
  void rejectsUnsafeModulePaths() throws Exception {
    byte[] moduleJar = "module-bytes".getBytes();
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    for (String unsafePath : new String[] {"lib/other.jar", "/module.jar", "", "dir\\module.jar"}) {
      Path bundle =
          bundleJar(
              manifestWithPaths(unsafePath, List.of(), moduleJar), entry("module.jar", moduleJar));
      assertThatThrownBy(() -> installation.installBundle(install(bundle)))
          .as("module path %s", unsafePath)
          .isInstanceOf(SourceModuleInstallationException.class)
          .hasMessageContaining("has an unsafe path");
    }
  }

  @Test
  void rejectsEscapingModulePath() throws Exception {
    byte[] moduleJar = "module-bytes".getBytes();
    Path bundle =
        bundleJar(
            manifestWithPaths("../module.jar", List.of(), moduleJar),
            entry("module.jar", moduleJar));
    var installation = new FileSystemBinaryArtifactInstallation(workspace);

    assertThatThrownBy(() -> installation.installBundle(install(bundle)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("has an unsafe path");
  }

  @Test
  void rejectsModuleDigestThatIsNotALowercaseSha256() throws Exception {
    byte[] moduleJar = "module-bytes".getBytes();
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    for (String badDigest :
        new String[] {"ABCDEF", "sha256-nothex", hex(moduleJar).toUpperCase()}) {
      String manifest = "module:\n  path: module.jar\n  sha256: %s".formatted(badDigest);
      Path bundle = bundleJar(manifest, entry("module.jar", moduleJar));
      assertThatThrownBy(() -> installation.installBundle(install(bundle)))
          .as("digest %s", badDigest)
          .isInstanceOf(SourceModuleInstallationException.class)
          .hasMessageContaining("lowercase SHA-256 digest");
    }
  }

  @Test
  void rejectsRuntimePathOutsideLib() throws Exception {
    byte[] moduleJar = "module-bytes".getBytes();
    byte[] runtimeJar = "runtime-bytes".getBytes();
    String manifest =
        """
        module:
          path: module.jar
          sha256: %s
        runtime:
          - path: other/runtime.jar
            sha256: %s
        """
            .formatted(hex(moduleJar), hex(runtimeJar));
    Path bundle =
        bundleJar(manifest, entry("module.jar", moduleJar), entry("lib/impl.jar", runtimeJar));
    var installation = new FileSystemBinaryArtifactInstallation(workspace);

    assertThatThrownBy(() -> installation.installBundle(install(bundle)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("has an unsafe path");
  }

  @Test
  void rejectsDuplicateMemberPaths() throws Exception {
    byte[] moduleJar = "module-bytes".getBytes();
    byte[] runtimeJar = "runtime-bytes".getBytes();
    String manifest =
        """
        module:
          path: module.jar
          sha256: %s
        runtime:
          - path: lib/impl.jar
            sha256: %s
          - path: lib/impl.jar
            sha256: %s
        """
            .formatted(hex(moduleJar), hex(runtimeJar), hex(runtimeJar));
    Path bundle =
        bundleJar(manifest, entry("module.jar", moduleJar), entry("lib/impl.jar", runtimeJar));
    var installation = new FileSystemBinaryArtifactInstallation(workspace);

    assertThatThrownBy(() -> installation.installBundle(install(bundle)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("duplicate member path");
  }

  @Test
  void rejectsDeclaredMemberMissingFromTheArchive() throws Exception {
    byte[] moduleJar = "module-bytes".getBytes();
    byte[] runtimeJar = "runtime-bytes".getBytes();
    String manifest =
        manifestWithPaths("module.jar", List.of("lib/missing.jar"), moduleJar, runtimeJar);
    Path bundle =
        bundleJar(manifest, entry("module.jar", moduleJar), entry("lib/impl.jar", runtimeJar));
    var installation = new FileSystemBinaryArtifactInstallation(workspace);

    assertThatThrownBy(() -> installation.installBundle(install(bundle)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("member is missing: lib/missing.jar");
  }

  @Test
  void rejectsMemberWhoseContentDoesNotMatchItsDeclaredDigest() throws Exception {
    byte[] moduleJar = "module-bytes".getBytes();
    byte[] runtimeJar = "runtime-bytes".getBytes();
    String manifest =
        """
        module:
          path: module.jar
          sha256: %s
        runtime:
          - path: lib/impl.jar
            sha256: %s
        """
            .formatted(hex(moduleJar), hex("tampered".getBytes()));
    Path bundle =
        bundleJar(manifest, entry("module.jar", moduleJar), entry("lib/impl.jar", runtimeJar));
    var installation = new FileSystemBinaryArtifactInstallation(workspace);

    assertThatThrownBy(() -> installation.installBundle(install(bundle)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("digest does not match expected digest");
  }

  @Test
  void rejectsUndeclaredArchiveMembers() throws Exception {
    byte[] moduleJar = "module-bytes".getBytes();
    byte[] runtimeJar = "runtime-bytes".getBytes();
    String manifest =
        manifestWithPaths("module.jar", List.of("lib/impl.jar"), moduleJar, runtimeJar);
    Path bundle =
        bundleJar(
            manifest,
            entry("module.jar", moduleJar),
            entry("lib/impl.jar", runtimeJar),
            entry("lib/extra.jar", "undeclared".getBytes()));
    var installation = new FileSystemBinaryArtifactInstallation(workspace);

    assertThatThrownBy(() -> installation.installBundle(install(bundle)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("undeclared member: lib/extra.jar");
  }

  @Test
  void acceptsAnEmbeddedModuleMetadataMemberAtTheBundleRoot() throws Exception {
    byte[] moduleJar = "module-bytes".getBytes();
    Path bundle =
        bundleJar(
            manifestWithPaths("module.jar", List.of(), moduleJar),
            entry("module.jar", moduleJar),
            entry("module-metadata.yaml", "schemaVersion: 1".getBytes()));
    var installation = new FileSystemBinaryArtifactInstallation(workspace);

    InstalledBundle installed = installation.installBundle(install(bundle));

    assertThat(installed.artifacts()).hasSize(1);
    assertThat(
            workspace.resolve(
                "source-module-installation/modules/bundle-module/1.0.0/module-metadata.yaml"))
        .as("embedded metadata is read during installation, never extracted")
        .doesNotExist();
  }

  @Test
  void rejectsAVersionThatIsAlreadyInstalled() throws Exception {
    byte[] moduleJar = "module-bytes".getBytes();
    Path bundle =
        bundleJar(
            manifestWithPaths("module.jar", List.of(), moduleJar), entry("module.jar", moduleJar));
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    installation.installBundle(install(bundle));

    assertThatThrownBy(() -> installation.installBundle(install(bundle)))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("already installed");
  }

  // --- multi-jar bundle install ---

  @Test
  void installsEveryMultiJarArtifactIntoOneStagedVersionDirectory() throws Exception {
    byte[] first = "first-module".getBytes();
    byte[] second = "second-runtime".getBytes();
    Path firstJar = write("first.jar", first);
    Path secondJar = write("second.jar", second);
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    BundleInstall bundle =
        new BundleInstall(
            List.of(
                new Install("multi-module", artifact("first"), firstJar.toString(), digest(first)),
                new Install(
                    "multi-module", artifact("second"), secondJar.toString(), digest(second))));

    InstalledBundle installed = installation.install(bundle);

    assertThat(installed.artifacts())
        .hasSize(2)
        .satisfiesExactly(
            firstArtifact ->
                assertThat(firstArtifact.path())
                    .contains("multi-module", "1.0.0", "first-1.0.0.jar"),
            secondArtifact ->
                assertThat(secondArtifact.path())
                    .contains("multi-module", "1.0.0", "second-1.0.0.jar"));
    Path versionDirectory =
        workspace.resolve("source-module-installation/modules/multi-module/1.0.0");
    assertThat(versionDirectory.resolve("first-1.0.0.jar")).isRegularFile();
    assertThat(versionDirectory.resolve("second-1.0.0.jar")).isRegularFile();
    try (Stream<Path> siblings = Files.list(versionDirectory.getParent())) {
      assertThat(siblings.filter(path -> path.getFileName().toString().contains("staging")))
          .isEmpty();
    }
  }

  @Test
  void rejectsMultiJarBundlesSpanningModules() throws Exception {
    Path firstJar = write("first.jar", "first-module".getBytes());
    Path secondJar = write("second.jar", "second-runtime".getBytes());
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    BundleInstall bundle =
        new BundleInstall(
            List.of(
                new Install(
                    "multi-module", artifact("first"), firstJar.toString(), digest("first-module")),
                new Install(
                    "other-module",
                    artifact("second"),
                    secondJar.toString(),
                    digest("second-runtime"))));

    assertThatThrownBy(() -> installation.install(bundle))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("must belong to one module");
  }

  @Test
  void rejectsDuplicateArtifactIdentitiesInsideOneBundle() throws Exception {
    Path firstJar = write("first.jar", "first-module".getBytes());
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    BundleInstall bundle =
        new BundleInstall(
            List.of(
                new Install(
                    "multi-module", artifact("same"), firstJar.toString(), digest("first-module")),
                new Install(
                    "multi-module",
                    artifact("same"),
                    firstJar.toString(),
                    digest("first-module"))));

    assertThatThrownBy(() -> installation.install(bundle))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("duplicate artifact identity");
  }

  @Test
  void rejectsMultiJarBundleWhoseSecondArtifactDigestDoesNotMatch() throws Exception {
    Path firstJar = write("first.jar", "first-module".getBytes());
    Path secondJar = write("second.jar", "second-runtime".getBytes());
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    BundleInstall bundle =
        new BundleInstall(
            List.of(
                new Install(
                    "multi-module", artifact("first"), firstJar.toString(), digest("first-module")),
                new Install(
                    "multi-module", artifact("second"), secondJar.toString(), digest("tampered"))));

    assertThatThrownBy(() -> installation.install(bundle))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("does not match expected digest");
  }

  @Test
  void rejectsMultiJarBundleForAnAlreadyInstalledVersion() throws Exception {
    Path firstJar = write("first.jar", "first-module".getBytes());
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    BundleInstall bundle =
        new BundleInstall(
            List.of(
                new Install(
                    "multi-module",
                    artifact("first"),
                    firstJar.toString(),
                    digest("first-module"))));
    installation.install(bundle);

    assertThatThrownBy(() -> installation.install(bundle))
        .isInstanceOf(SourceModuleInstallationException.class)
        .hasMessageContaining("already installed");
  }

  // --- discard ---

  @Test
  void discardRemovesTheManagedBundleDirectory() throws Exception {
    byte[] moduleJar = "module-bytes".getBytes();
    Path bundle =
        bundleJar(
            manifestWithPaths("module.jar", List.of(), moduleJar), entry("module.jar", moduleJar));
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    InstalledBundle installed = installation.installBundle(install(bundle));

    installation.discard(installed);

    assertThat(workspace.resolve("source-module-installation/modules/bundle-module/1.0.0"))
        .doesNotExist();
  }

  @Test
  void discardIgnoresAnEmptyBundle() {
    var installation = new FileSystemBinaryArtifactInstallation(workspace);

    installation.discard(new InstalledBundle(List.of()));

    assertThat(workspace.resolve("source-module-installation")).doesNotExist();
  }

  @Test
  void discardIgnoresArtifactsOutsideTheManagedModuleRoot() {
    var installation = new FileSystemBinaryArtifactInstallation(workspace);

    installation.discard(
        new InstalledBundle(
            List.of(new InstalledArtifact("/tmp/elsewhere/a-1.0.0.jar", "sha256:x"))));

    assertThat(workspace.resolve("source-module-installation")).doesNotExist();
  }

  @Test
  void discardIgnoresArtifactsSpreadAcrossDirectories() throws Exception {
    var installation = new FileSystemBinaryArtifactInstallation(workspace);
    Path kept = write("kept.jar", "kept".getBytes());
    installation.install(
        new Install("kept-module", artifact("kept"), kept.toString(), digest("kept")));

    installation.discard(
        new InstalledBundle(
            List.of(
                new InstalledArtifact(
                    workspace
                        .resolve(
                            "source-module-installation/modules/kept-module/1.0.0/kept-1.0.0.jar")
                        .toString(),
                    "sha256:x"),
                new InstalledArtifact(
                    workspace
                        .resolve(
                            "source-module-installation/modules/kept-module/2.0.0/kept-2.0.0.jar")
                        .toString(),
                    "sha256:x"))));

    assertThat(workspace.resolve("source-module-installation/modules/kept-module/1.0.0"))
        .isDirectory();
  }

  @Test
  void discardIgnoresAMissingManagedDirectory() {
    var installation = new FileSystemBinaryArtifactInstallation(workspace);

    installation.discard(
        new InstalledBundle(
            List.of(
                new InstalledArtifact(
                    workspace
                        .resolve("source-module-installation/modules/absent-module/1.0.0/a.jar")
                        .toString(),
                    "sha256:x"))));

    assertThat(workspace.resolve("source-module-installation/modules/absent-module"))
        .doesNotExist();
  }

  // --- helpers ---

  private static SourceModuleIndex.Artifact artifact(String artifactId) {
    return new SourceModuleIndex.Artifact("ai.sea.modules", artifactId, "1.0.0");
  }

  private Install install(Path bundleJar) {
    return new Install(
        "bundle-module",
        artifact("bundle"),
        bundleJar.toString(),
        "sha256:" + hex(readAll(bundleJar)));
  }

  private static byte[] readAll(Path path) {
    try {
      return Files.readAllBytes(path);
    } catch (java.io.IOException exception) {
      throw new AssertionError(exception);
    }
  }

  private Path write(String fileName, byte[] content) throws Exception {
    Path path = workspace.resolve(fileName);
    Files.write(path, content);
    return path;
  }

  private Path bundleJar(String manifest, Object[]... members) throws Exception {
    Path path = workspace.resolve("bundle-" + System.nanoTime() + ".jar");
    try (var out = new java.util.jar.JarOutputStream(Files.newOutputStream(path))) {
      if (manifest != null) {
        out.putNextEntry(new java.util.jar.JarEntry(MANIFEST_ENTRY));
        out.write(manifest.getBytes());
        out.closeEntry();
      }
      for (Object[] member : members) {
        out.putNextEntry(new java.util.jar.JarEntry((String) member[0]));
        out.write((byte[]) member[1]);
        out.closeEntry();
      }
    }
    return path;
  }

  private static Object[] entry(String name, byte[] content) {
    return new Object[] {name, content};
  }

  private static String manifestWithPaths(
      String modulePath, List<String> runtimePaths, byte[] moduleJar) {
    return manifestWithPaths(modulePath, runtimePaths, moduleJar, "runtime-bytes".getBytes());
  }

  private static String manifestWithPaths(
      String modulePath, List<String> runtimePaths, byte[] moduleJar, byte[] runtimeJar) {
    StringBuilder yaml = new StringBuilder();
    yaml.append("module:\n  path: ").append(modulePath);
    if (!modulePath.isBlank()
        && !modulePath.startsWith("/")
        && !modulePath.contains("..")
        && !modulePath.contains("\\")) {
      yaml.append("\n  sha256: ").append(hex(moduleJar));
    }
    if (runtimePaths != null && !runtimePaths.isEmpty()) {
      yaml.append("\nruntime:");
      for (String path : runtimePaths) {
        yaml.append("\n  - path: ").append(path);
        if (path.startsWith("lib/")) {
          yaml.append("\n    sha256: ").append(hex(runtimeJar));
        }
      }
    }
    return yaml.toString();
  }

  private static String digest(byte[] content) {
    return "sha256:" + hex(content);
  }

  private static String digest(String content) {
    return digest(content.getBytes());
  }

  private static String hex(String content) {
    return hex(content.getBytes());
  }

  private static String hex(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new AssertionError(exception);
    }
  }
}
