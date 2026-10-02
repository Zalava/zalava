package org.zalava.knowledge.skills.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One actor's explicit, durable activation of a discovered skill.
 *
 * <p>Activation captures the validated, bounded content and the descriptor version it was selected
 * from, so the selection is auditable and can be rolled back by deactivation. Activation grants no
 * tool, scope or permission: it only records that a SEA-owned, policy-checked skill body may be
 * offered to the owning actor's agent context as untrusted instructions.
 */
public record SkillActivation(
    String actorId,
    String name,
    String version,
    String contentDigest,
    String content,
    SkillActivationState state,
    String activatedAt,
    String updatedAt,
    List<Event> history) {

  public record Event(String type, String at, String detail) {
    public Event {
      if (type == null || type.isBlank()) {
        throw new IllegalArgumentException("event type must not be blank");
      }
      if (at == null || at.isBlank()) {
        throw new IllegalArgumentException("event timestamp must not be blank");
      }
      detail = detail == null ? "" : detail;
    }
  }

  public SkillActivation {
    if (actorId == null || actorId.isBlank()) {
      throw new IllegalArgumentException("actorId must not be blank");
    }
    if (name == null || name.isBlank()) {
      throw new IllegalArgumentException("name must not be blank");
    }
    if (version == null || version.isBlank()) {
      throw new IllegalArgumentException("version must not be blank");
    }
    if (contentDigest == null || contentDigest.isBlank()) {
      throw new IllegalArgumentException("contentDigest must not be blank");
    }
    if (content == null || content.isBlank()) {
      throw new IllegalArgumentException("content must not be blank");
    }
    Objects.requireNonNull(state, "state must not be null");
    if (activatedAt == null || activatedAt.isBlank()) {
      throw new IllegalArgumentException("activatedAt must not be blank");
    }
    if (updatedAt == null || updatedAt.isBlank()) {
      throw new IllegalArgumentException("updatedAt must not be blank");
    }
    history = List.copyOf(Objects.requireNonNull(history, "history must not be null"));
  }

  public static SkillActivation activated(
      String actorId,
      String name,
      String version,
      String contentDigest,
      String content,
      String now) {
    return new SkillActivation(
        actorId,
        name,
        version,
        contentDigest,
        content,
        SkillActivationState.ACTIVE,
        now,
        now,
        List.of(new Event("activated", now, version)));
  }

  public boolean active() {
    return state == SkillActivationState.ACTIVE;
  }

  /** Records a re-selection of the same skill at a newer version or content digest. */
  public SkillActivation refreshed(
      String version, String contentDigest, String content, String now) {
    return transition(
        SkillActivationState.ACTIVE,
        version,
        contentDigest,
        content,
        now,
        new Event("refreshed", now, version));
  }

  /** Restores a previously deactivated skill. */
  public SkillActivation reactivated(
      String version, String contentDigest, String content, String now) {
    return transition(
        SkillActivationState.ACTIVE,
        version,
        contentDigest,
        content,
        now,
        new Event("reactivated", now, version));
  }

  public SkillActivation deactivated(String now) {
    if (!active()) {
      return this;
    }
    return transition(
        SkillActivationState.DEACTIVATED,
        version,
        contentDigest,
        content,
        now,
        new Event("deactivated", now, ""));
  }

  private SkillActivation transition(
      SkillActivationState nextState,
      String nextVersion,
      String nextDigest,
      String nextContent,
      String now,
      Event event) {
    List<Event> updatedHistory = new ArrayList<>(history);
    updatedHistory.add(event);
    return new SkillActivation(
        actorId,
        name,
        nextVersion,
        nextDigest,
        nextContent,
        nextState,
        activatedAt,
        now,
        updatedHistory);
  }
}
