package org.zalava.discovery.adapter.in.http;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import org.zalava.discovery.CapabilityGapEvidence;
import org.zalava.discovery.application.port.in.CapabilityGapEvidenceQueries;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Development/test-only, read-only inspection of persisted capability-gap evidence. */
@RestController
@RequestMapping("/api/sea/capability-gap")
@Profile({"dev", "test"})
public class CapabilityGapAdminController {

  private final CapabilityGapEvidenceQueries evidence;

  public CapabilityGapAdminController(CapabilityGapEvidenceQueries evidence) {
    this.evidence = evidence;
  }

  @GetMapping("/evidence")
  public List<EvidenceResponse> evidence(
      @RequestParam(name = "limit", defaultValue = "20") int limit) {
    try {
      return evidence.recent(limit).stream().map(EvidenceResponse::from).toList();
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }
  }

  public record EvidenceResponse(
      String queryDigest,
      Instant occurredAt,
      int installedMatchCount,
      String classification,
      String detail,
      List<CandidateResponse> candidates) {
    static EvidenceResponse from(CapabilityGapEvidence evidence) {
      return new EvidenceResponse(
          evidence.queryDigest(),
          evidence.occurredAt(),
          evidence.installedMatchCount(),
          evidence.classification().name().toLowerCase(Locale.ROOT),
          evidence.detail(),
          evidence.candidates().stream().map(CandidateResponse::from).toList());
    }
  }

  public record CandidateResponse(
      String moduleId, String version, String digest, int rank, String reason) {
    static CandidateResponse from(CapabilityGapEvidence.RankedCandidate candidate) {
      return new CandidateResponse(
          candidate.moduleId(),
          candidate.version(),
          candidate.digest(),
          candidate.rank(),
          candidate.reason());
    }
  }
}
