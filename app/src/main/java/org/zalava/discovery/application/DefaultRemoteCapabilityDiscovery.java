package org.zalava.discovery.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.zalava.discovery.CapabilityGapClassification;
import org.zalava.discovery.CapabilityGapEvidence;
import org.zalava.discovery.CapabilityGapEvidence.RankedCandidate;
import org.zalava.discovery.RemoteCatalogException;
import org.zalava.discovery.RemoteModuleCandidate;
import org.zalava.discovery.application.port.in.RemoteCapabilityDiscovery;
import org.zalava.discovery.application.port.out.CapabilityGapEvidenceStore;
import org.zalava.discovery.application.port.out.RemoteModuleCatalog;

public final class DefaultRemoteCapabilityDiscovery implements RemoteCapabilityDiscovery {

  private static final int MAX_CANDIDATES_LIMIT = 50;
  private static final int MAX_DETAIL_LENGTH = 200;

  private final RemoteModuleCatalog catalog;
  private final RemoteCandidatePolicy policy;
  private final CapabilityGapEvidenceStore evidenceStore;
  private final Clock clock;
  private final int maxCandidates;

  public DefaultRemoteCapabilityDiscovery(
      RemoteModuleCatalog catalog,
      RemoteCandidatePolicy policy,
      CapabilityGapEvidenceStore evidenceStore,
      Clock clock,
      int maxCandidates) {
    this.catalog = Objects.requireNonNull(catalog, "catalog");
    this.policy = Objects.requireNonNull(policy, "policy");
    this.evidenceStore = Objects.requireNonNull(evidenceStore, "evidenceStore");
    this.clock = Objects.requireNonNull(clock, "clock");
    if (maxCandidates < 1 || maxCandidates > MAX_CANDIDATES_LIMIT) {
      throw new IllegalArgumentException(
          "Remote capability discovery maxCandidates must be between 1 and "
              + MAX_CANDIDATES_LIMIT);
    }
    this.maxCandidates = maxCandidates;
  }

  @Override
  public Outcome discover(String query, int installedMatchCount) {
    if (query == null || query.isBlank()) {
      throw new IllegalArgumentException("Capability-gap discovery query must not be blank");
    }
    if (installedMatchCount < 0) {
      throw new IllegalArgumentException("installedMatchCount must not be negative");
    }
    String normalizedQuery = normalize(query);
    Instant occurredAt = clock.instant();
    String queryDigest = "sha256:" + sha256(normalizedQuery);

    if (installedMatchCount > 0) {
      return Outcome.of(CapabilityGapClassification.INSTALLED_MATCH);
    }
    if (!catalog.configured()) {
      return Outcome.of(CapabilityGapClassification.DISABLED);
    }

    List<RemoteModuleCandidate> candidates;
    try {
      candidates = catalog.lookup(normalizedQuery);
    } catch (RemoteCatalogException exception) {
      CapabilityGapEvidence evidence =
          new CapabilityGapEvidence(
              queryDigest,
              occurredAt,
              0,
              CapabilityGapClassification.UNAVAILABLE,
              safeDetail(exception),
              List.of());
      evidenceStore.save(evidence);
      return Outcome.of(CapabilityGapClassification.UNAVAILABLE);
    }

    List<RankedCandidate> ranked =
        rank(normalizedQuery, candidates == null ? List.of() : candidates);
    CapabilityGapClassification classification =
        ranked.isEmpty()
            ? CapabilityGapClassification.NO_MATCH
            : CapabilityGapClassification.WEAK_MATCH;
    CapabilityGapEvidence evidence =
        new CapabilityGapEvidence(
            queryDigest,
            occurredAt,
            0,
            classification,
            classification == CapabilityGapClassification.WEAK_MATCH
                ? "remote module candidate(s) are recommendation-only and require administrator "
                    + "installation approval"
                : "no eligible remote module candidate",
            ranked);
    evidenceStore.save(evidence);
    return new Outcome(classification, ranked);
  }

  private List<RankedCandidate> rank(
      String normalizedQuery, List<RemoteModuleCandidate> candidates) {
    List<String> terms = Arrays.stream(normalizedQuery.split(" ")).distinct().toList();
    List<Scored> scored = new ArrayList<>();
    for (RemoteModuleCandidate candidate : candidates) {
      if (candidate != null && policy.eligible(candidate)) {
        Scored value = score(normalizedQuery, terms, candidate);
        if (value.score() > 0) {
          scored.add(value);
        }
      }
    }
    List<Scored> ordered =
        scored.stream()
            .sorted(
                Comparator.comparingInt(Scored::score)
                    .reversed()
                    .thenComparing(value -> value.candidate().moduleId())
                    .thenComparing(value -> value.candidate().version()))
            .limit(maxCandidates)
            .toList();
    List<RankedCandidate> ranked = new ArrayList<>();
    for (int index = 0; index < ordered.size(); index++) {
      Scored value = ordered.get(index);
      RemoteModuleCandidate candidate = value.candidate();
      ranked.add(
          new RankedCandidate(
              candidate.moduleId(),
              candidate.version(),
              candidate.digest(),
              index + 1,
              value.reason()));
    }
    return List.copyOf(ranked);
  }

  private static Scored score(
      String normalizedQuery, List<String> terms, RemoteModuleCandidate candidate) {
    String moduleId = normalize(candidate.moduleId());
    String displayName = normalize(candidate.displayName());
    String description = normalize(candidate.description());
    String permissions = normalize(String.join(" ", candidate.permissions()));

    int score = 0;
    List<String> reasons = new ArrayList<>();
    if (moduleId.equals(normalizedQuery)) {
      score += 1_000;
      reasons.add("moduleId-exact");
    } else if (containsPhrase(moduleId, normalizedQuery)) {
      score += 400;
      reasons.add("moduleId");
    }
    score += termScore(terms, moduleId, 120, reasons, "moduleId");
    score += termScore(terms, displayName, 40, reasons, "displayName");
    score += termScore(terms, description, 20, reasons, "description");
    score += termScore(terms, permissions, 10, reasons, "permissions");
    return new Scored(score, String.join("+", reasons.stream().distinct().toList()), candidate);
  }

  private static int termScore(
      List<String> terms, String candidate, int weight, List<String> reasons, String reason) {
    int matched = matchedTerms(terms, candidate);
    if (matched > 0) {
      reasons.add(reason);
    }
    return matched * weight;
  }

  private static int matchedTerms(List<String> terms, String candidate) {
    int matched = 0;
    for (String term : terms) {
      if (!term.isBlank() && containsPhrase(candidate, term)) {
        matched++;
      }
    }
    return matched;
  }

  private static boolean containsPhrase(String candidate, String phrase) {
    return (" " + candidate + " ").contains(" " + phrase + " ");
  }

  private static String safeDetail(RemoteCatalogException exception) {
    String message = exception.getMessage();
    String detail = "remote module catalog unavailable";
    if (message != null && !message.isBlank()) {
      detail = detail + ": " + message;
    }
    detail = detail.replaceAll("\\s+", " ").trim();
    return detail.length() > MAX_DETAIL_LENGTH ? detail.substring(0, MAX_DETAIL_LENGTH) : detail;
  }

  private static String normalize(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^a-z0-9]+", " ")
        .trim()
        .replaceAll(" +", " ");
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 digest is unavailable", exception);
    }
  }

  private record Scored(int score, String reason, RemoteModuleCandidate candidate) {}
}
