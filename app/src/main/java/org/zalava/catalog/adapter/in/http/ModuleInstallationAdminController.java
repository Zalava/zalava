package org.zalava.catalog.adapter.in.http;

import java.net.URI;
import org.zalava.catalog.LocalArtifactInstallRequest;
import org.zalava.catalog.LocalArtifactModuleMetadataLoader;
import org.zalava.catalog.ModuleReleaseInstallRequest;
import org.zalava.catalog.SourceModuleIndex;
import org.zalava.catalog.install.SourceModuleInstallationException;
import org.zalava.catalog.install.application.port.in.LocalArtifactModuleInstallation;
import org.zalava.catalog.install.application.port.in.ModuleLocatorInstallation;
import org.zalava.catalog.install.application.port.in.ModuleReleaseInstallation;
import org.zalava.development.DevelopmentRequestId;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/sea")
@Profile({"dev", "test"})
public class ModuleInstallationAdminController {

  private final LocalArtifactModuleInstallation localArtifactModuleInstallation;
  private final ModuleReleaseInstallation moduleReleaseInstallation;
  private final ModuleLocatorInstallation moduleLocatorInstallation;

  public ModuleInstallationAdminController(
      LocalArtifactModuleInstallation localArtifactModuleInstallation,
      ModuleReleaseInstallation moduleReleaseInstallation,
      ModuleLocatorInstallation moduleLocatorInstallation) {
    this.localArtifactModuleInstallation = localArtifactModuleInstallation;
    this.moduleReleaseInstallation = moduleReleaseInstallation;
    this.moduleLocatorInstallation = moduleLocatorInstallation;
  }

  @PostMapping("/local-module-installations")
  public ResponseEntity<LocalArtifactInstallationResponse> createLocalModuleInstallation(
      @RequestBody LocalArtifactInstallationRequest request) {
    try {
      SourceModuleIndex index = new LocalArtifactModuleMetadataLoader().load(request.indexYaml());
      SourceModuleIndex.Module module =
          index.modules().stream()
              .filter(item -> item.moduleId().equals(request.moduleId()))
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalArgumentException(
                          "Module id is not present in the validated index: "
                              + request.moduleId()));
      LocalArtifactInstallRequest created =
          localArtifactModuleInstallation.create(
              module,
              request.artifactPath(),
              new DevelopmentRequestId(request.developmentRequestId()));
      return ResponseEntity.created(
              URI.create("/api/sea/local-module-installations/" + created.requestId()))
          .body(LocalArtifactInstallationResponse.from(created));
    } catch (IllegalArgumentException | SourceModuleInstallationException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    }
  }

  @GetMapping("/local-module-installations/{requestId}")
  public LocalArtifactInstallationResponse localModuleInstallation(@PathVariable String requestId) {
    try {
      return LocalArtifactInstallationResponse.from(localArtifactModuleInstallation.get(requestId));
    } catch (SourceModuleInstallationException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    }
  }

  @PostMapping("/local-module-installations/{requestId}/allow")
  public LocalArtifactInstallationResponse allowLocalModuleInstallation(
      @PathVariable String requestId) {
    try {
      return LocalArtifactInstallationResponse.from(
          localArtifactModuleInstallation.allow(requestId));
    } catch (SourceModuleInstallationException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
    }
  }

  @PostMapping("/local-module-installations/{requestId}/deny")
  public LocalArtifactInstallationResponse denyLocalModuleInstallation(
      @PathVariable String requestId) {
    try {
      return LocalArtifactInstallationResponse.from(
          localArtifactModuleInstallation.deny(requestId));
    } catch (SourceModuleInstallationException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
    }
  }

  @PostMapping("/module-release-installations")
  public ResponseEntity<ModuleReleaseInstallationResponse> createModuleReleaseInstallation(
      @RequestBody ModuleReleaseInstallationRequest request) {
    try {
      ModuleReleaseInstallRequest created =
          moduleLocatorInstallation.create(
              new ModuleLocatorInstallation.Request(
                  request.moduleId(), request.version(), request.developmentRequestId()));
      return ResponseEntity.created(
              URI.create("/api/sea/module-release-installations/" + created.requestId()))
          .body(ModuleReleaseInstallationResponse.from(created));
    } catch (IllegalArgumentException | SourceModuleInstallationException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    }
  }

  @GetMapping("/module-release-installations/{requestId}")
  public ModuleReleaseInstallationResponse moduleReleaseInstallation(
      @PathVariable String requestId) {
    try {
      return ModuleReleaseInstallationResponse.from(moduleReleaseInstallation.get(requestId));
    } catch (SourceModuleInstallationException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    }
  }

  @PostMapping("/module-release-installations/{requestId}/allow")
  public ModuleReleaseInstallationResponse allowModuleReleaseInstallation(
      @PathVariable String requestId) {
    try {
      return ModuleReleaseInstallationResponse.from(moduleReleaseInstallation.allow(requestId));
    } catch (SourceModuleInstallationException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
    }
  }

  @PostMapping("/module-release-installations/{requestId}/deny")
  public ModuleReleaseInstallationResponse denyModuleReleaseInstallation(
      @PathVariable String requestId) {
    try {
      return ModuleReleaseInstallationResponse.from(moduleReleaseInstallation.deny(requestId));
    } catch (SourceModuleInstallationException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
    }
  }

  public record LocalArtifactInstallationRequest(
      String moduleId, String indexYaml, String artifactPath, String developmentRequestId) {}

  public record LocalArtifactInstallationResponse(
      String requestId,
      String moduleId,
      String artifactPath,
      String artifactDigest,
      String status,
      String message) {
    static LocalArtifactInstallationResponse from(LocalArtifactInstallRequest request) {
      return new LocalArtifactInstallationResponse(
          request.requestId(),
          request.module().moduleId(),
          request.artifactPath(),
          request.artifactDigest(),
          request.status().name().toLowerCase(),
          request.message());
    }
  }

  public record ModuleReleaseInstallationRequest(
      String moduleId, String version, String developmentRequestId) {}

  public record ModuleReleaseInstallationResponse(
      String requestId,
      String moduleId,
      String version,
      URI manifestUri,
      String repositoryId,
      String artifactDigest,
      String status,
      String message) {
    static ModuleReleaseInstallationResponse from(ModuleReleaseInstallRequest request) {
      return new ModuleReleaseInstallationResponse(
          request.requestId(),
          request.module().moduleId(),
          request.module().version(),
          request.manifestUri(),
          request.repositoryId(),
          request.artifactDigest(),
          request.status().name().toLowerCase(),
          request.message());
    }
  }
}
