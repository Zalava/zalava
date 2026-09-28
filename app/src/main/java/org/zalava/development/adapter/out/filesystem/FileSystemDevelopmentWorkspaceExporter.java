package org.zalava.development.adapter.out.filesystem;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.zalava.development.DevelopmentRequestException;
import org.zalava.development.DevelopmentWorkspace;
import org.zalava.development.ModuleDevelopmentRequest;
import org.zalava.development.application.port.out.DevelopmentWorkspacePort;
import tools.jackson.databind.ObjectMapper;

/** Materializes only SEA-owned request inputs below {@code .sea-request}. */
@Component
public final class FileSystemDevelopmentWorkspaceExporter implements DevelopmentWorkspacePort {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final List<String> PUBLIC_FILES =
      List.of(
          "docs/module-api-release-guide.md",
          "examples/minimal-module/README.md",
          "examples/minimal-module/build.gradle",
          "examples/minimal-module/settings.gradle",
          "examples/minimal-module/module-metadata.yaml");

  @Override
  public synchronized DevelopmentWorkspace materialize(
      ModuleDevelopmentRequest request, String workspaceRoot) {
    if (request == null)
      throw new DevelopmentRequestException("development request must not be null");
    if (workspaceRoot == null || workspaceRoot.isBlank()) {
      throw new DevelopmentRequestException(
          "workspace root must be an absolute user-selected path");
    }
    var requestId = request.id();
    Path selectedRoot = Path.of(workspaceRoot);
    if (!selectedRoot.isAbsolute()) {
      throw new DevelopmentRequestException(
          "workspace root must be an absolute user-selected path");
    }
    Path root = selectedRoot.normalize();
    Path provided = root.resolve(".sea-request");
    try {
      if (Files.exists(root) && !Files.isDirectory(root))
        throw new DevelopmentRequestException("workspace root is not a directory");
      Files.createDirectories(provided);
      Files.createDirectories(root.resolve("delivery"));
      Map<String, String> hashes = new LinkedHashMap<>();
      byte[] requestJson = prettyJson(request);
      byte[] contract = prettyJson(request.currentRevision().contract());
      byte[] scenarios =
          prettyJson(
              Map.of(
                  "requestId",
                  requestId.value(),
                  "acceptanceScenarios",
                  request.currentRevision().contract().acceptanceScenarios()));
      write(provided, "request.json", requestJson);
      write(provided, "development-contract.yaml", contract);
      write(provided, "acceptance-scenarios.yaml", scenarios);
      hashes.put("development-contract.yaml", sha256(contract));
      hashes.put("acceptance-scenarios.yaml", sha256(scenarios));
      write(provided, "sdk/module-api.coordinates", moduleApiCoordinates(request));
      write(provided, "schemas/tool-contract.schema.json", toolContractSchema());
      write(provided, "schemas/module-metadata.schema.yaml", moduleMetadataSchema());
      write(provided, "instructions/error-envelope.md", errorEnvelope());
      write(provided, "README.md", readme(request));
      write(provided, "CODEX_TASK.md", codexTask(request));
      for (String file : PUBLIC_FILES) copyPublicResource(provided, file);
      write(
          provided,
          "traceability.json",
          prettyJson(
              Map.of(
                  "requestId",
                  requestId.value(),
                  "authoritativeSource",
                  "SEA persisted development request",
                  "sha256",
                  hashes)));
      return new DevelopmentWorkspace(requestId, root.toString(), hashes);
    } catch (IOException ex) {
      throw new DevelopmentRequestException("Unable to export development workspace: " + root, ex);
    }
  }

  private static byte[] prettyJson(Object value) throws IOException {
    return JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
  }

  private static void write(Path requestRoot, String relative, byte[] content) throws IOException {
    Path target = requestRoot.resolve(relative).normalize();
    if (!target.startsWith(requestRoot))
      throw new DevelopmentRequestException("invalid request export path: " + relative);
    Files.createDirectories(target.getParent());
    Path temporary = Files.createTempFile(target.getParent(), ".sea-request-", ".tmp");
    try {
      Files.write(temporary, content);
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException ignored) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static void copyPublicResource(Path requestRoot, String relative) throws IOException {
    try (InputStream input =
        FileSystemDevelopmentWorkspaceExporter.class
            .getClassLoader()
            .getResourceAsStream("development-workspace/" + relative)) {
      if (input == null)
        throw new DevelopmentRequestException(
            "Missing packaged public workspace resource: " + relative);
      write(requestRoot, relative, input.readAllBytes());
    }
  }

  private static String sha256(byte[] content) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
      StringBuilder hexadecimal = new StringBuilder(digest.length * 2);
      for (byte value : digest) hexadecimal.append(String.format("%02x", value));
      return hexadecimal.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is unavailable", ex);
    }
  }

  private static byte[] toolContractSchema() {
    return "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"object\",\"required\":[\"name\",\"inputSchema\",\"outputSchema\"]}"
        .getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] moduleMetadataSchema() {
    return """
        schemaVersion: 1
        # Local artifact metadata does not require source-clone provenance.
        # services.provided and services.required declare typed SEA service contracts.
        # Service implementations remain module-local and must not bundle module-api.
        """
        .getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] errorEnvelope() {
    return "# SEA error envelope\n\nReturn an explicit, stable error code for expected failures. Do not expose secrets or host paths.\n"
        .getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] moduleApiCoordinates(ModuleDevelopmentRequest request) {
    return ("org.zalava:module-api:"
            + request.currentRevision().contract().targetSeaApiVersion()
            + "\n")
        .getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] readme(ModuleDevelopmentRequest request) {
    return ("# SEA development request "
            + request.id().value()
            + "\n\n"
            + "This package is informational. SEA's persisted request is authoritative during validation.\n"
            + "Place the final package in `../delivery/` according to `development-contract.yaml`.\n")
        .getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] codexTask(ModuleDevelopmentRequest request) {
    return ("# Implement SEA module "
            + request.currentRevision().contract().module().moduleId()
            + "\n\n"
            + "1. Read this entire `.sea-request/` package.\n"
            + "2. Implement the module outside the SEA repository.\n"
            + "3. Ask the user about product decisions unresolved by the contract.\n"
            + "4. Choose the internal architecture and build layout, then write and run module tests.\n"
            + "5. Build the final package and place it in `delivery/`.\n"
            + "6. Do not edit the exported contract or acceptance scenarios merely to pass validation.\n\n"
            + "The user runs Codex manually from this workspace; do not assume SEA source access, Git, or automatic installation.\n")
        .getBytes(StandardCharsets.UTF_8);
  }
}
