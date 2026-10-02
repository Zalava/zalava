package org.zalava.modules.catalog.install.adapter.out.filesystem;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.zalava.modules.catalog.install.SourceModuleInstallationException;
import org.zalava.modules.catalog.install.application.port.out.BinaryArtifactInstallation;

public final class FileSystemBinaryArtifactInstallation implements BinaryArtifactInstallation {

  private static final Pattern PATH_SEGMENT = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

  private final Path managedRoot;
  private final Path modulesRoot;

  public FileSystemBinaryArtifactInstallation(Path workspace) {
    this.managedRoot = workspace.toAbsolutePath().normalize().resolve("source-module-installation");
    this.modulesRoot = managedRoot.resolve("modules");
  }

  @Override
  public InstalledArtifact install(Install install) {
    validateSegment(install.moduleId(), "module id");
    validateSegment(install.artifact().artifactId(), "artifact id");
    validateSegment(install.artifact().version(), "artifact version");
    Path sourceArtifact = validateSourceArtifact(install.sourceArtifact());
    validateDigest(sourceArtifact, install.expectedDigest());

    String filename = install.artifact().artifactId() + "-" + install.artifact().version() + ".jar";
    Path destinationDirectory =
        modulesRoot.resolve(install.moduleId()).resolve(install.artifact().version()).normalize();
    Path destination = destinationDirectory.resolve(filename);
    Path temporary = destinationDirectory.resolve(filename + ".tmp");

    try {
      createManagedDestination(destinationDirectory);
      Files.copy(sourceArtifact, temporary, StandardCopyOption.REPLACE_EXISTING);
      String digest = digest(temporary);
      if (!digest.equals(install.expectedDigest())) {
        throw new SourceModuleInstallationException(
            "Binary artifact digest changed during installation");
      }
      AtomicFileOperations.replace(temporary, destination);
      return new InstalledArtifact(destination.toString(), digest);
    } catch (IOException ex) {
      throw new SourceModuleInstallationException(
          "Unable to install binary module artifact: " + filename, ex);
    } finally {
      try {
        Files.deleteIfExists(temporary);
      } catch (IOException ignored) {
        // Preserve the primary installation failure.
      }
    }
  }

  @Override
  public InstalledBundle installBundle(Install bundle) {
    validateSegment(bundle.moduleId(), "module id");
    validateSegment(bundle.artifact().version(), "artifact version");
    Path source = validateSourceArtifact(bundle.sourceArtifact());
    validateDigest(source, bundle.expectedDigest());
    Path destination =
        modulesRoot.resolve(bundle.moduleId()).resolve(bundle.artifact().version()).normalize();
    Path staging =
        destination.resolveSibling(destination.getFileName() + ".staging-" + UUID.randomUUID());
    try (java.util.jar.JarFile archive = new java.util.jar.JarFile(source.toFile())) {
      BundleManifest manifest = bundleManifest(archive);
      createManagedDestination(destination.getParent());
      if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
        throw new SourceModuleInstallationException(
            "Binary artifact bundle version is already installed");
      }
      createDirectoryWithoutSymlink(staging);
      List<InstalledArtifact> installed = new ArrayList<>();
      extract(archive, manifest.module(), staging, destination, installed);
      for (BundleEntry runtime : manifest.runtime()) {
        extract(archive, runtime, staging, destination, installed);
      }
      rejectUndeclaredMembers(archive, manifest);
      Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
      return new InstalledBundle(installed);
    } catch (IOException exception) {
      throw new SourceModuleInstallationException(
          "Unable to install binary artifact bundle", exception);
    } finally {
      deleteStaging(staging);
    }
  }

  @Override
  public InstalledBundle install(BundleInstall bundle) {
    List<Install> artifacts = bundle.artifacts();
    Install primary = artifacts.getFirst();
    validateSegment(primary.moduleId(), "module id");
    validateSegment(primary.artifact().version(), "artifact version");
    Path destination =
        modulesRoot.resolve(primary.moduleId()).resolve(primary.artifact().version()).normalize();
    Path staging =
        destination.resolveSibling(destination.getFileName() + ".staging-" + UUID.randomUUID());
    try {
      createManagedDestination(destination.getParent());
      if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
        throw new SourceModuleInstallationException(
            "Binary artifact bundle version is already installed");
      }
      createDirectoryWithoutSymlink(staging);
      Set<String> identities = new HashSet<>();
      List<InstalledArtifact> installed = new ArrayList<>();
      for (Install artifact : artifacts) {
        validateBundleArtifact(primary, artifact, identities);
        Path source = validateSourceArtifact(artifact.sourceArtifact());
        validateDigest(source, artifact.expectedDigest());
        String filename =
            artifact.artifact().artifactId() + '-' + artifact.artifact().version() + ".jar";
        Path target = staging.resolve(filename).normalize();
        if (!target.getParent().equals(staging)) {
          throw new SourceModuleInstallationException(
              "Binary artifact bundle path escapes staging directory");
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        validateDigest(target, artifact.expectedDigest());
        installed.add(
            new InstalledArtifact(
                destination.resolve(filename).toString(), artifact.expectedDigest()));
      }
      Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
      return new InstalledBundle(installed);
    } catch (IOException exception) {
      throw new SourceModuleInstallationException(
          "Unable to install binary artifact bundle", exception);
    } finally {
      deleteStaging(staging);
    }
  }

  @Override
  public void discard(InstalledBundle bundle) {
    if (bundle.artifacts().isEmpty()) {
      return;
    }
    Path directory =
        Path.of(bundle.artifacts().getFirst().path()).toAbsolutePath().normalize().getParent();
    try {
      if (directory == null
          || !directory.startsWith(modulesRoot)
          || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
        return;
      }
      for (InstalledArtifact artifact : bundle.artifacts()) {
        if (!Path.of(artifact.path()).toAbsolutePath().normalize().getParent().equals(directory)) {
          return;
        }
      }
      try (var paths = Files.walk(directory)) {
        paths
            .sorted(java.util.Comparator.reverseOrder())
            .forEach(FileSystemBinaryArtifactInstallation::deleteQuietly);
      }
    } catch (IOException ignored) {
      // Preserve the original installation failure; an unreferenced managed bundle is harmless.
    }
  }

  private static BundleManifest bundleManifest(java.util.jar.JarFile archive) {
    java.util.jar.JarEntry entry = archive.getJarEntry("META-INF/zalava-module-bundle.yaml");
    if (entry == null || entry.isDirectory()) {
      throw new SourceModuleInstallationException("Binary artifact bundle manifest is required");
    }
    try (InputStream input = archive.getInputStream(entry)) {
      Object loaded =
          new org.yaml.snakeyaml.Yaml(
                  new org.yaml.snakeyaml.constructor.SafeConstructor(
                      new org.yaml.snakeyaml.LoaderOptions()))
              .load(input);
      if (!(loaded instanceof java.util.Map<?, ?> root)) {
        throw new SourceModuleInstallationException(
            "Binary artifact bundle manifest must be an object");
      }
      BundleEntry module = bundleEntry(root.get("module"), "module", true);
      List<BundleEntry> runtime = new ArrayList<>();
      Object rawRuntime = root.get("runtime");
      if (rawRuntime != null) {
        if (!(rawRuntime instanceof List<?> entries)) {
          throw new SourceModuleInstallationException(
              "Binary artifact bundle runtime must be a list");
        }
        for (int index = 0; index < entries.size(); index++) {
          runtime.add(bundleEntry(entries.get(index), "runtime[" + index + "]", false));
        }
      }
      Set<String> paths = new HashSet<>();
      paths.add(module.path());
      for (BundleEntry runtimeEntry : runtime) {
        if (!paths.add(runtimeEntry.path())) {
          throw new SourceModuleInstallationException(
              "Binary artifact bundle contains duplicate member path");
        }
      }
      return new BundleManifest(module, List.copyOf(runtime));
    } catch (IOException | org.yaml.snakeyaml.error.YAMLException exception) {
      throw new SourceModuleInstallationException(
          "Unable to read binary artifact bundle manifest", exception);
    }
  }

  private static BundleEntry bundleEntry(Object value, String field, boolean module) {
    if (!(value instanceof java.util.Map<?, ?> map)) {
      throw new SourceModuleInstallationException(
          "Binary artifact bundle " + field + " must be an object");
    }
    Object rawPath = map.get("path");
    Object rawDigest = map.get("sha256");
    if (!(rawPath instanceof String path)
        || !safeBundlePath(path)
        || (module && !"module.jar".equals(path))
        || (!module && !path.startsWith("lib/"))) {
      throw new SourceModuleInstallationException(
          "Binary artifact bundle " + field + " has an unsafe path");
    }
    if (!(rawDigest instanceof String digest) || !digest.matches("[a-f0-9]{64}")) {
      throw new SourceModuleInstallationException(
          "Binary artifact bundle " + field + " must declare a lowercase SHA-256 digest");
    }
    return new BundleEntry(path, "sha256:" + digest);
  }

  private static boolean safeBundlePath(String path) {
    return !path.isBlank()
        && !path.startsWith("/")
        && !path.contains("\\")
        && !path.contains("//")
        && !path.contains("../")
        && !path.startsWith("../")
        && path.endsWith(".jar");
  }

  private static void extract(
      java.util.jar.JarFile archive,
      BundleEntry entry,
      Path staging,
      Path destination,
      List<InstalledArtifact> installed)
      throws IOException {
    java.util.jar.JarEntry member = archive.getJarEntry(entry.path());
    if (member == null || member.isDirectory()) {
      throw new SourceModuleInstallationException(
          "Binary artifact bundle member is missing: " + entry.path());
    }
    Path target = staging.resolve(entry.path()).normalize();
    if (!target.startsWith(staging)) {
      throw new SourceModuleInstallationException(
          "Binary artifact bundle member path escapes staging directory");
    }
    Files.createDirectories(target.getParent());
    try (InputStream input = archive.getInputStream(member)) {
      Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
    }
    validateDigest(target, entry.digest());
    installed.add(
        new InstalledArtifact(destination.resolve(entry.path()).toString(), entry.digest()));
  }

  private static void rejectUndeclaredMembers(
      java.util.jar.JarFile archive, BundleManifest manifest) {
    Set<String> declared = new HashSet<>();
    declared.add("META-INF/zalava-module-bundle.yaml");
    // The module embeds its own metadata document at the bundle root; SEA reads it during
    // installation and never extracts it into the managed module directory.
    declared.add("module-metadata.yaml");
    declared.add(manifest.module().path());
    manifest.runtime().forEach(entry -> declared.add(entry.path()));
    java.util.Enumeration<java.util.jar.JarEntry> entries = archive.entries();
    while (entries.hasMoreElements()) {
      java.util.jar.JarEntry entry = entries.nextElement();
      if (entry.isDirectory() || "META-INF/MANIFEST.MF".equals(entry.getName())) {
        continue;
      }
      if (!declared.contains(entry.getName())) {
        throw new SourceModuleInstallationException(
            "Binary artifact bundle contains undeclared member: " + entry.getName());
      }
    }
  }

  private record BundleManifest(BundleEntry module, List<BundleEntry> runtime) {}

  private record BundleEntry(String path, String digest) {}

  private static void validateBundleArtifact(
      Install primary, Install artifact, Set<String> identities) {
    if (!primary.moduleId().equals(artifact.moduleId())) {
      throw new SourceModuleInstallationException(
          "Binary artifact bundle must belong to one module");
    }
    validateSegment(artifact.artifact().artifactId(), "artifact id");
    validateSegment(artifact.artifact().version(), "artifact version");
    String identity =
        artifact.artifact().groupId()
            + ':'
            + artifact.artifact().artifactId()
            + ':'
            + artifact.artifact().version();
    if (!identities.add(identity)) {
      throw new SourceModuleInstallationException(
          "Binary artifact bundle contains duplicate artifact identity");
    }
  }

  private static void deleteStaging(Path staging) {
    if (!Files.exists(staging, LinkOption.NOFOLLOW_LINKS)) {
      return;
    }
    try (var paths = Files.walk(staging)) {
      paths
          .sorted(java.util.Comparator.reverseOrder())
          .forEach(FileSystemBinaryArtifactInstallation::deleteQuietly);
    } catch (IOException ignored) {
      // Preserve the primary installation failure.
    }
  }

  private static void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException ignored) {
      // Best effort cleanup of a known staging path.
    }
  }

  private static Path validateSourceArtifact(String artifact) {
    if (artifact == null) {
      throw new SourceModuleInstallationException("Binary artifact path is required");
    }
    Path normalized = Path.of(artifact).toAbsolutePath().normalize();
    if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)
        || Files.isSymbolicLink(normalized)) {
      throw new SourceModuleInstallationException("Binary artifact must be a regular file");
    }
    try {
      return normalized.toRealPath(LinkOption.NOFOLLOW_LINKS);
    } catch (IOException ex) {
      throw new SourceModuleInstallationException("Unable to validate binary artifact path", ex);
    }
  }

  private static void validateDigest(Path artifact, String expectedDigest) {
    try {
      String actual = digest(artifact);
      if (!actual.equals(expectedDigest)) {
        throw new SourceModuleInstallationException(
            "Binary artifact digest does not match expected digest");
      }
    } catch (IOException ex) {
      throw new SourceModuleInstallationException("Unable to validate binary artifact digest", ex);
    }
  }

  private void createManagedDestination(Path destinationDirectory) throws IOException {
    createDirectoryWithoutSymlink(managedRoot);
    createDirectoryWithoutSymlink(modulesRoot);
    Path moduleDirectory = destinationDirectory.getParent();
    createDirectoryWithoutSymlink(moduleDirectory);
    createDirectoryWithoutSymlink(destinationDirectory);
    Path realRoot = modulesRoot.toRealPath(LinkOption.NOFOLLOW_LINKS);
    Path realDestination = destinationDirectory.toRealPath(LinkOption.NOFOLLOW_LINKS);
    if (!realDestination.startsWith(realRoot)) {
      throw new SourceModuleInstallationException(
          "Binary module destination must be inside the managed module directory");
    }
  }

  private static void createDirectoryWithoutSymlink(Path directory) throws IOException {
    if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(directory)) {
      throw new SourceModuleInstallationException(
          "Managed module directory must not be a symbolic link: " + directory);
    }
    Files.createDirectories(directory);
    if (Files.isSymbolicLink(directory)) {
      throw new SourceModuleInstallationException(
          "Managed module directory must not be a symbolic link: " + directory);
    }
  }

  private static String digest(Path path) throws IOException {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (InputStream input = Files.newInputStream(path)) {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer, 0, buffer.length)) >= 0) {
          digest.update(buffer, 0, read);
        }
      }
      return "sha256:" + HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
  }

  private static void validateSegment(String value, String field) {
    if (value == null
        || !PATH_SEGMENT.matcher(value).matches()
        || value.equals(".")
        || value.equals("..")) {
      throw new SourceModuleInstallationException("Invalid " + field + ": " + value);
    }
  }
}
