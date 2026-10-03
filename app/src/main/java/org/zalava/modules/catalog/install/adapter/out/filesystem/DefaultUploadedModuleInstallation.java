package org.zalava.modules.catalog.install.adapter.out.filesystem;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.zalava.modules.catalog.LocalArtifactModuleMetadataLoader;
import org.zalava.modules.catalog.ModuleReleaseInstallRequest;
import org.zalava.modules.catalog.SourceModuleIndex;
import org.zalava.modules.catalog.install.SourceModuleInstallationException;
import org.zalava.modules.catalog.install.application.port.in.UploadedModuleInstallation;
import org.zalava.modules.catalog.install.application.port.out.ModuleReleaseInstallRequestStore;

/** Approval-gated administrator upload source; uploaded artifacts are never enabled on receipt. */
public final class DefaultUploadedModuleInstallation implements UploadedModuleInstallation {
  static final long MAX_BYTES = 100L * 1024 * 1024;

  private final Path uploads;
  private final ModuleReleaseInstallRequestStore requests;
  private final Clock clock;
  private final long maximumBytes;
  private final LocalArtifactModuleMetadataLoader metadata =
      new LocalArtifactModuleMetadataLoader();

  public DefaultUploadedModuleInstallation(
      Path workspace, ModuleReleaseInstallRequestStore requests, Clock clock) {
    this(workspace, requests, clock, MAX_BYTES);
  }

  DefaultUploadedModuleInstallation(
      Path workspace, ModuleReleaseInstallRequestStore requests, Clock clock, long maximumBytes) {
    this.uploads = workspace.resolve("source-module-installation/uploads");
    this.requests = requests;
    this.clock = clock;
    this.maximumBytes = maximumBytes;
  }

  @Override
  public synchronized ModuleReleaseInstallRequest create(Request request) {
    if (request.content() == null) {
      throw new SourceModuleInstallationException("Uploaded module JAR is required");
    }
    Path artifact = null;
    try (InputStream content = request.content()) {
      Files.createDirectories(uploads);
      artifact = uploads.resolve(UUID.randomUUID() + ".jar");
      String digest = stage(content, artifact);
      SourceModuleIndex index = metadata.loadJar(artifact);
      if (index.modules().size() != 1) {
        throw new SourceModuleInstallationException("Uploaded JAR must declare exactly one module");
      }
      SourceModuleIndex.Module module = index.modules().getFirst();
      boolean artifactBundle = isBundle(artifact);
      return requests.create(
          new ModuleReleaseInstallRequest(
              UUID.randomUUID().toString(),
              clock.instant(),
              URI.create("local-upload://" + artifact.getFileName()),
              module,
              artifact.toString(),
              digest,
              List.of(),
              artifactBundle,
              "local-upload",
              null,
              0,
              org.zalava.modules.development.CandidateEvaluation.Decision.ACCEPTED,
              ModuleReleaseInstallRequest.Status.PENDING,
              null,
              artifactBundle
                  ? "Awaiting approval for locally uploaded module bundle"
                  : "Awaiting approval for locally uploaded JAR"));
    } catch (IOException exception) {
      throw new SourceModuleInstallationException("Unable to stage uploaded module JAR", exception);
    } catch (RuntimeException exception) {
      delete(artifact);
      throw exception;
    }
  }

  private String stage(InputStream content, Path artifact) throws IOException {
    MessageDigest digest = sha256();
    long copied = 0;
    try (DigestInputStream input = new DigestInputStream(content, digest);
        var output = Files.newOutputStream(artifact)) {
      byte[] buffer = new byte[8192];
      for (int read; (read = input.read(buffer)) != -1; ) {
        copied += read;
        if (copied > maximumBytes) {
          throw new SourceModuleInstallationException(
              "Uploaded module JAR must not exceed " + maximumBytes + " bytes");
        }
        output.write(buffer, 0, read);
      }
    }
    if (copied == 0) {
      throw new SourceModuleInstallationException("Uploaded module JAR is required");
    }
    return "sha256:" + HexFormat.of().formatHex(digest.digest());
  }

  private static boolean isBundle(Path artifact) {
    try (java.util.jar.JarFile jar = new java.util.jar.JarFile(artifact.toFile())) {
      return jar.getJarEntry("META-INF/zalava-module-bundle.yaml") != null
          || jar.getJarEntry("META-INF/sea-module-bundle.yaml") != null;
    } catch (IOException exception) {
      throw new SourceModuleInstallationException(
          "Uploaded module JAR must be a readable JAR", exception);
    }
  }

  private static MessageDigest sha256() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  private static void delete(Path artifact) {
    if (artifact == null) {
      return;
    }
    try {
      Files.deleteIfExists(artifact);
    } catch (IOException ignored) {
      // The staging directory is private and unreferenced artifacts are harmless.
    }
  }
}
