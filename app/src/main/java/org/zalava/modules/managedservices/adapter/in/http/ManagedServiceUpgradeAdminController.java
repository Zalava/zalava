package org.zalava.modules.managedservices.adapter.in.http;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.zalava.modules.managedservices.application.ManagedServiceUpgradeException;
import org.zalava.modules.managedservices.application.ManagedServiceUpgradeRequest;
import org.zalava.modules.managedservices.application.ManagedServiceUpgradeValidationException;
import org.zalava.modules.managedservices.application.port.in.ManagedServiceDiagnostics;
import org.zalava.modules.managedservices.application.port.in.ManagedServiceUpgrade;

/**
 * Administrator HTTP surface for managed-service upgrades and bounded diagnostics (dev/test
 * profiles). Mutations carry the generation they were planned on, so stale operations are rejected
 * instead of silently racing a concurrent decision.
 */
@RestController
@RequestMapping("/api/sea")
@Profile({"dev", "test"})
public class ManagedServiceUpgradeAdminController {

  private final ManagedServiceUpgrade upgrades;
  private final ManagedServiceDiagnostics diagnostics;

  public ManagedServiceUpgradeAdminController(
      ManagedServiceUpgrade upgrades, ManagedServiceDiagnostics diagnostics) {
    this.upgrades = upgrades;
    this.diagnostics = diagnostics;
  }

  @PostMapping("/managed-service-upgrades")
  public ResponseEntity<ManagedServiceUpgradeResponse> plan(
      @RequestBody ManagedServiceUpgradePlanRequest request) {
    try {
      ManagedServiceUpgradeRequest created =
          upgrades.plan(
              new ManagedServiceUpgrade.UpgradePlanRequest(
                  request.candidates().stream()
                      .map(
                          candidate ->
                              new ManagedServiceUpgrade.UpgradeCandidate(
                                  candidate.moduleId(),
                                  candidate.serviceId(),
                                  candidate.candidateState()))
                      .toList()));
      return ResponseEntity.status(HttpStatus.CREATED)
          .body(ManagedServiceUpgradeResponse.from(created));
    } catch (ManagedServiceUpgradeValidationException | IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    } catch (ManagedServiceUpgradeException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
    }
  }

  @GetMapping("/managed-service-upgrades/{requestId}")
  public ManagedServiceUpgradeResponse get(@PathVariable String requestId) {
    try {
      return ManagedServiceUpgradeResponse.from(upgrades.get(requestId));
    } catch (ManagedServiceUpgrade.UnknownUpgradeRequestException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    }
  }

  @GetMapping("/managed-service-upgrades")
  public List<ManagedServiceUpgradeResponse> recent() {
    return upgrades.recent(50).stream().map(ManagedServiceUpgradeResponse::from).toList();
  }

  @PostMapping("/managed-service-upgrades/{requestId}/allow")
  public ManagedServiceUpgradeResponse allow(
      @PathVariable String requestId, @RequestParam long generation) {
    try {
      return ManagedServiceUpgradeResponse.from(upgrades.allow(requestId, generation));
    } catch (ManagedServiceUpgrade.UnknownUpgradeRequestException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    } catch (ManagedServiceUpgradeValidationException | IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    } catch (ManagedServiceUpgradeException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
    }
  }

  @PostMapping("/managed-service-upgrades/{requestId}/deny")
  public ManagedServiceUpgradeResponse deny(
      @PathVariable String requestId, @RequestParam long generation) {
    try {
      return ManagedServiceUpgradeResponse.from(upgrades.deny(requestId, generation));
    } catch (ManagedServiceUpgrade.UnknownUpgradeRequestException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    } catch (ManagedServiceUpgradeValidationException | IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    } catch (ManagedServiceUpgradeException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
    }
  }

  @GetMapping("/managed-services/{serviceId}")
  public ManagedServiceDiagnostics.DiagnosticsReport inspect(@PathVariable String serviceId) {
    try {
      return diagnostics.inspect(serviceId);
    } catch (ManagedServiceDiagnostics.UnknownServiceException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    }
  }

  @GetMapping("/managed-services/{serviceId}/logs")
  public List<String> logs(
      @PathVariable String serviceId, @RequestParam(defaultValue = "100") int lines) {
    try {
      return diagnostics.recentLogs(serviceId, lines);
    } catch (ManagedServiceDiagnostics.UnknownServiceException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    }
  }

  @PostMapping("/managed-services/{serviceId}/restart")
  public ManagedServiceDiagnostics.DiagnosticsReport restart(@PathVariable String serviceId) {
    try {
      return diagnostics.restart(serviceId);
    } catch (ManagedServiceDiagnostics.UnknownServiceException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    } catch (ManagedServiceUpgradeException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
    }
  }

  public record CandidateRequestBody(
      String moduleId,
      String serviceId,
      org.zalava.managed.ManagedServiceDesiredState candidateState) {}

  public record ManagedServiceUpgradePlanRequest(List<CandidateRequestBody> candidates) {}

  public record ManagedServiceUpgradeResponse(
      String requestId,
      long generation,
      String status,
      String message,
      List<PlannedUpgradeSummary> services) {

    static ManagedServiceUpgradeResponse from(ManagedServiceUpgradeRequest request) {
      return new ManagedServiceUpgradeResponse(
          request.requestId(),
          request.generation(),
          request.status().name().toLowerCase(),
          request.message(),
          request.services().stream()
              .map(
                  planned ->
                      new PlannedUpgradeSummary(
                          planned.moduleId(),
                          planned.serviceId(),
                          planned.candidateState().revision(),
                          planned.previousState().revision(),
                          planned.phase().name(),
                          planned.backupLocation()))
              .toList());
    }
  }

  public record PlannedUpgradeSummary(
      String moduleId,
      String serviceId,
      String candidateRevision,
      String previousRevision,
      String phase,
      String backupLocation) {}
}
