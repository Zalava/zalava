package org.zalava.knowledge.skills.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.zalava.identity.accounts.domain.AccountRole;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.knowledge.skills.application.port.in.SkillActivations;
import org.zalava.knowledge.skills.application.port.in.SkillQueries;
import org.zalava.knowledge.skills.application.port.out.SkillActivationStore;
import org.zalava.knowledge.skills.application.port.out.SkillContentSource;
import org.zalava.knowledge.skills.domain.SkillActivation;
import org.zalava.knowledge.skills.domain.SkillActivationDeniedException;
import org.zalava.knowledge.skills.domain.SkillContentPolicy;
import org.zalava.knowledge.skills.domain.SkillDescriptor;
import org.zalava.knowledge.skills.domain.SkillStaleVersionException;
import org.zalava.knowledge.skills.domain.SkillVisibility;

/**
 * Actor-owned, policy-controlled skill activation authority.
 *
 * <p>Activation resolves the highest discovered version, enforces actor visibility and the content
 * budget, and persists the validated body for the requesting actor only. It is idempotent for an
 * unchanged active selection, refreshes stale content, and deactivation rolls the selection back.
 * Enabling a skill never grants a tool, scope or permission: it records that Zalava may offer the
 * body to that actor's context as untrusted instructions.
 */
public final class ZalavaSkillActivations implements SkillActivations {

  private final SkillQueries skills;
  private final SkillContentSource contents;
  private final SkillActivationStore store;
  private final Supplier<Instant> clock;
  private final int maximumContentCharacters;

  private int activations;
  private int refreshes;
  private int reactivations;
  private int deactivations;
  private int denials;
  private int staleVersions;
  private int budgetRejections;
  private int contentCharacters;

  public ZalavaSkillActivations(
      SkillQueries skills,
      SkillContentSource contents,
      SkillActivationStore store,
      Supplier<Instant> clock,
      int maximumContentCharacters) {
    this.skills = Objects.requireNonNull(skills, "skills must not be null");
    this.contents = Objects.requireNonNull(contents, "contents must not be null");
    this.store = Objects.requireNonNull(store, "store must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
    if (maximumContentCharacters < 1) {
      throw new IllegalArgumentException("A skill content budget must be positive");
    }
    this.maximumContentCharacters = maximumContentCharacters;
  }

  @Override
  public synchronized SkillActivation activate(
      Actor actor, AccountRole role, String name, String expectedVersion) {
    requireActor(actor);
    Objects.requireNonNull(role, "role must not be null");
    String normalized = requireName(name);
    SkillDescriptor descriptor =
        skills.describe(normalized).orElseThrow(() -> new NotFoundException(normalized));
    if (descriptor.visibility() == SkillVisibility.ADMIN && role != AccountRole.ADMIN) {
      denials++;
      throw new SkillActivationDeniedException(normalized);
    }
    if (expectedVersion != null
        && !expectedVersion.isBlank()
        && !expectedVersion.strip().equals(descriptor.version())) {
      staleVersions++;
      throw new SkillStaleVersionException(
          normalized, expectedVersion.strip(), descriptor.version());
    }
    String content = contents.load(normalized).orElseThrow(() -> new NotFoundException(normalized));
    try {
      SkillContentPolicy.validate(normalized, content, maximumContentCharacters);
    } catch (SkillContentPolicy.SkillContentBudgetExceededException budget) {
      budgetRejections++;
      throw budget;
    }
    String now = clock.get().toString();
    String digest = digest(content);
    Optional<SkillActivation> existing = store.find(actor, normalized);
    SkillActivation activation;
    if (existing.isEmpty()) {
      activation =
          SkillActivation.activated(
              actor.accountId().toString(), normalized, descriptor.version(), digest, content, now);
      activations++;
    } else {
      SkillActivation current = existing.get();
      if (current.active()
          && current.version().equals(descriptor.version())
          && current.contentDigest().equals(digest)) {
        return current;
      }
      if (current.active()) {
        activation = current.refreshed(descriptor.version(), digest, content, now);
        refreshes++;
      } else {
        activation = current.reactivated(descriptor.version(), digest, content, now);
        reactivations++;
      }
    }
    SkillActivation saved = store.save(activation);
    contentCharacters += content.length();
    return saved;
  }

  @Override
  public synchronized SkillActivation deactivate(Actor actor, String name) {
    requireActor(actor);
    String normalized = requireName(name);
    SkillActivation activation =
        store.find(actor, normalized).orElseThrow(() -> new NotFoundException(normalized));
    if (!activation.active()) {
      return activation;
    }
    SkillActivation saved = store.save(activation.deactivated(clock.get().toString()));
    deactivations++;
    return saved;
  }

  @Override
  public synchronized List<SkillActivation> active(Actor actor) {
    requireActor(actor);
    return store.list(actor).stream()
        .filter(SkillActivation::active)
        .sorted(Comparator.comparing(SkillActivation::name))
        .toList();
  }

  @Override
  public synchronized Optional<SkillActivation> find(Actor actor, String name) {
    requireActor(actor);
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    return store.find(actor, name.strip());
  }

  @Override
  public synchronized SkillActivationMetrics metrics() {
    return new SkillActivationMetrics(
        activations,
        refreshes,
        reactivations,
        deactivations,
        denials,
        staleVersions,
        budgetRejections,
        contentCharacters);
  }

  private static void requireActor(Actor actor) {
    if (actor == null) {
      throw new IllegalArgumentException("A skill activation actor is required");
    }
  }

  private static String requireName(String name) {
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("A skill name is required");
    }
    String normalized = name.strip();
    if (normalized.length() > 100) {
      throw new IllegalArgumentException("A skill name must not exceed 100 characters");
    }
    return normalized;
  }

  private static String digest(String content) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable", exception);
    }
  }

  public static final class NotFoundException extends RuntimeException {
    public NotFoundException(String name) {
      super("Zalava skill not found: " + name);
    }
  }
}
