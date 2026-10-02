package org.zalava.modules.managedservices.adapter.in.http;

import java.net.URI;
import java.util.List;
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
import org.zalava.api.extensions.managed.ManagedServiceDesiredState;
import org.zalava.api.extensions.managed.ManagedServiceResourceGrant;
import org.zalava.modules.managedservices.application.ManagedServiceInstallException;
import org.zalava.modules.managedservices.application.ManagedServiceInstallPlanningException;
import org.zalava.modules.managedservices.application.ManagedServiceInstallRequest;
import org.zalava.modules.managedservices.application.port.in.ManagedServiceInstallation;
import org.zalava.modules.managedservices.application.port.in.ManagedServiceInstallation.PlannedRequest;

/** Administrator HTTP surface for the managed-service install workflow (dev/test profiles). */
@RestController
@RequestMapping("/api/sea")
@Profile({"dev", "test"})
public class ManagedServiceInstallationAdminController {

  private final ManagedServiceInstallation installations;

  public ManagedServiceInstallationAdminController(ManagedServiceInstallation installations) {
    this.installations = installations;
  }

  @PostMapping("/managed-service-installations")
  public ResponseEntity<ManagedServiceInstallationResponse> plan(
      @RequestBody ManagedServiceInstallationPlanRequest request) {
    try {
      ManagedServiceInstallRequest created =
          installations.plan(
              new ManagedServiceInstallation.PlanRequest(
                  request.requests().stream()
                      .map(
                          item ->
                              new PlannedRequest(
                                  item.moduleId(),
                                  item.serviceId(),
                                  item.desiredState(),
                                  item.grant(),
                                  java.util.Set.copyOf(item.dependsOn())))
                      .toList()));
      return ResponseEntity.created(
              URI.create("/api/sea/managed-service-installations/" + created.requestId()))
          .body(ManagedServiceInstallationResponse.from(created));
    } catch (ManagedServiceInstallPlanningException | IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    } catch (ManagedServiceInstallException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
    }
  }

  @GetMapping("/managed-service-installations/{requestId}")
  public ManagedServiceInstallationResponse get(@PathVariable String requestId) {
    try {
      return ManagedServiceInstallationResponse.from(installations.get(requestId));
    } catch (ManagedServiceInstallException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    }
  }

  @GetMapping("/managed-service-installations")
  public List<ManagedServiceInstallationResponse> recent() {
    return installations.recent(50).stream().map(ManagedServiceInstallationResponse::from).toList();
  }

  @PostMapping("/managed-service-installations/{requestId}/allow")
  public ManagedServiceInstallationResponse allow(@PathVariable String requestId) {
    try {
      return ManagedServiceInstallationResponse.from(installations.allow(requestId));
    } catch (ManagedServiceInstallException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
    }
  }

  @PostMapping("/managed-service-installations/{requestId}/deny")
  public ManagedServiceInstallationResponse deny(@PathVariable String requestId) {
    try {
      return ManagedServiceInstallationResponse.from(installations.deny(requestId));
    } catch (ManagedServiceInstallException ex) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
    }
  }

  public record PlannedServiceRequestBody(
      String moduleId,
      String serviceId,
      ManagedServiceDesiredState desiredState,
      ManagedServiceResourceGrant grant,
      List<String> dependsOn) {}

  public record ManagedServiceInstallationPlanRequest(List<PlannedServiceRequestBody> requests) {}

  public record ManagedServiceInstallationResponse(
      String requestId,
      List<PlannedServiceSummary> services,
      AggregatedResourcesSummary aggregate,
      String status,
      String message) {

    static ManagedServiceInstallationResponse from(ManagedServiceInstallRequest request) {
      return new ManagedServiceInstallationResponse(
          request.requestId(),
          request.services().stream()
              .map(
                  planned ->
                      new PlannedServiceSummary(
                          planned.moduleId(),
                          planned.serviceId(),
                          planned.desiredRevision(),
                          planned.grantRevision(),
                          planned.desiredState().artifactReference(),
                          List.copyOf(planned.dependsOn())))
              .toList(),
          new AggregatedResourcesSummary(
              request.aggregate().ports(),
              request.aggregate().secretReferences(),
              request.aggregate().dataPaths(),
              request.aggregate().devices(),
              request.aggregate().totalLimits().cpuMillis(),
              request.aggregate().totalLimits().memoryBytes(),
              request.aggregate().totalLimits().processLimit()),
          request.status().name().toLowerCase(),
          request.message());
    }
  }

  public record PlannedServiceSummary(
      String moduleId,
      String serviceId,
      String desiredRevision,
      String grantRevision,
      String artifactReference,
      List<String> dependsOn) {}

  public record AggregatedResourcesSummary(
      java.util.Set<Integer> ports,
      java.util.Set<String> secretReferences,
      java.util.Set<String> dataPaths,
      java.util.Set<String> devices,
      long totalCpuMillis,
      long totalMemoryBytes,
      int totalProcessLimit) {}
}
