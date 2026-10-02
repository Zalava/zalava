package org.zalava.knowledge.skills.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.knowledge.skills.application.port.in.SkillQueries;
import org.zalava.knowledge.skills.application.port.out.SkillCatalog;
import org.zalava.knowledge.skills.domain.SkillDescriptor;
import org.zalava.knowledge.skills.domain.SkillStatus;
import org.zalava.knowledge.skills.domain.SkillVisibility;

/**
 * Deterministic, bounded, actor/policy-aware skill discovery.
 *
 * <p>Installed and remote catalogues are merged by identity: duplicate names collapse to the
 * highest version, malformed candidates never reach this layer (the catalogue validates them), and
 * ordering is stable by name. Visibility is enforced here so a non-administrator never receives an
 * admin-only descriptor. Search is a bounded lexical match over name, description and declared
 * capabilities; descriptors carry metadata only and never execute a referenced tool.
 */
public final class DefaultSkillDiscovery implements SkillQueries {

  public static final int DEFAULT_MAX_RESULTS = 100;

  private final SkillCatalog localCatalog;
  private final SkillCatalog remoteCatalog;
  private final Set<String> activeNames;
  private final int maxResults;

  public DefaultSkillDiscovery(
      SkillCatalog localCatalog, SkillCatalog remoteCatalog, Set<String> activeNames) {
    this(localCatalog, remoteCatalog, activeNames, DEFAULT_MAX_RESULTS);
  }

  public DefaultSkillDiscovery(
      SkillCatalog localCatalog,
      SkillCatalog remoteCatalog,
      Set<String> activeNames,
      int maxResults) {
    this.localCatalog = Objects.requireNonNull(localCatalog, "localCatalog must not be null");
    this.remoteCatalog = Objects.requireNonNull(remoteCatalog, "remoteCatalog must not be null");
    this.activeNames =
        Set.copyOf(Objects.requireNonNull(activeNames, "activeNames must not be null"));
    if (maxResults < 1) {
      throw new IllegalArgumentException("maxResults must be positive");
    }
    this.maxResults = maxResults;
  }

  @Override
  public List<SkillDescriptor> installed(AccountRole role) {
    return deduplicate(localCatalog.discover()).stream()
        .filter(descriptor -> visible(descriptor, role))
        .map(descriptor -> descriptor.withStatus(SkillStatus.INSTALLED))
        .limit(maxResults)
        .toList();
  }

  @Override
  public List<SkillDescriptor> active(AccountRole role) {
    return installed(role).stream()
        .filter(descriptor -> activeNames.contains(descriptor.name()))
        .map(descriptor -> descriptor.withStatus(SkillStatus.ACTIVE))
        .toList();
  }

  @Override
  public List<SkillDescriptor> remote(AccountRole role) {
    return deduplicate(remoteCatalog.discover()).stream()
        .filter(descriptor -> visible(descriptor, role))
        .map(descriptor -> descriptor.withStatus(SkillStatus.REMOTE))
        .limit(maxResults)
        .toList();
  }

  @Override
  public List<SkillDescriptor> search(AccountRole role, String query, int limit) {
    if (limit < 1) {
      throw new IllegalArgumentException("limit must be positive");
    }
    List<String> terms = terms(query);
    List<SkillDescriptor> candidates = new ArrayList<>(installed(role));
    candidates.addAll(remote(role));
    if (terms.isEmpty()) {
      return candidates.stream().limit(Math.min(limit, maxResults)).toList();
    }
    return candidates.stream()
        .map(descriptor -> new Scored(descriptor, score(descriptor, terms)))
        .filter(scored -> scored.score() > 0)
        .sorted(SCORED_ORDER)
        .limit(Math.min(limit, maxResults))
        .map(Scored::descriptor)
        .toList();
  }

  @Override
  public Optional<SkillDescriptor> find(AccountRole role, String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    String normalized = name.strip();
    return installed(role).stream()
        .filter(descriptor -> descriptor.name().equals(normalized))
        .findFirst()
        .or(
            () ->
                remote(role).stream()
                    .filter(descriptor -> descriptor.name().equals(normalized))
                    .findFirst());
  }

  @Override
  public Optional<SkillDescriptor> describe(String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    String normalized = name.strip();
    List<SkillDescriptor> combined = new ArrayList<>(deduplicate(localCatalog.discover()));
    combined.addAll(deduplicate(remoteCatalog.discover()));
    return deduplicate(combined).stream()
        .filter(descriptor -> descriptor.name().equals(normalized))
        .findFirst();
  }

  private static List<SkillDescriptor> deduplicate(List<SkillDescriptor> discovered) {
    Map<String, SkillDescriptor> byName = new LinkedHashMap<>();
    for (SkillDescriptor descriptor : discovered) {
      byName.merge(descriptor.name(), descriptor, DefaultSkillDiscovery::higherVersion);
    }
    return byName.values().stream().sorted(Comparator.comparing(SkillDescriptor::name)).toList();
  }

  private static SkillDescriptor higherVersion(SkillDescriptor left, SkillDescriptor right) {
    int comparison = compareVersions(left.version(), right.version());
    if (comparison > 0) {
      return left;
    }
    if (comparison < 0) {
      return right;
    }
    return left.provenance().reference().compareTo(right.provenance().reference()) <= 0
        ? left
        : right;
  }

  static int compareVersions(String left, String right) {
    String[] leftParts = left.split("[.+-]", -1);
    String[] rightParts = right.split("[.+-]", -1);
    int length = Math.max(leftParts.length, rightParts.length);
    for (int index = 0; index < length; index++) {
      String leftPart = index < leftParts.length ? leftParts[index] : "0";
      String rightPart = index < rightParts.length ? rightParts[index] : "0";
      int comparison = comparePart(leftPart, rightPart);
      if (comparison != 0) {
        return comparison;
      }
    }
    return 0;
  }

  private static int comparePart(String left, String right) {
    boolean leftNumeric = left.matches("\\d+");
    boolean rightNumeric = right.matches("\\d+");
    if (leftNumeric && rightNumeric) {
      return Integer.compare(Integer.parseInt(left), Integer.parseInt(right));
    }
    return left.compareTo(right);
  }

  private static boolean visible(SkillDescriptor descriptor, AccountRole role) {
    return descriptor.visibility() == SkillVisibility.ALL || role == AccountRole.ADMIN;
  }

  private static int score(SkillDescriptor descriptor, List<String> terms) {
    String name = descriptor.name().toLowerCase(Locale.ROOT);
    String description = descriptor.description().toLowerCase(Locale.ROOT);
    List<String> capabilities =
        descriptor.capabilities().stream().map(value -> value.toLowerCase(Locale.ROOT)).toList();
    int score = 0;
    for (String term : terms) {
      if (name.contains(term)) score += 4;
      if (description.contains(term)) score += 2;
      if (capabilities.stream().anyMatch(value -> value.contains(term))) score += 1;
    }
    return score;
  }

  private static List<String> terms(String query) {
    if (query == null || query.isBlank()) {
      return List.of();
    }
    List<String> terms = new ArrayList<>();
    for (String token : query.toLowerCase(Locale.ROOT).split("[^\\p{Alnum}]+")) {
      if (!token.isBlank() && !terms.contains(token)) {
        terms.add(token);
      }
    }
    return terms;
  }

  private static final Comparator<Scored> SCORED_ORDER =
      Comparator.comparingInt(Scored::score)
          .reversed()
          .thenComparing(scored -> scored.descriptor().name());

  private record Scored(SkillDescriptor descriptor, int score) {}
}
