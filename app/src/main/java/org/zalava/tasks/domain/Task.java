package org.zalava.tasks.domain;

import java.time.Instant;
import java.util.Optional;

public class Task {

  private static final String FEEDBACK_LABEL = "Agent feedback:";
  private static final int MAX_FAILURE_DETAIL_LENGTH = 500;

  public enum Status {
    todo,
    in_progress,
    completed,
    awaiting_human_input,
    cancelled,
    failed
  }

  private final String id;
  private final String name;
  private final Instant createdAt;
  private final Status status;
  private final String description;
  private final String agentFeedback;
  private final String failureDetail;

  public Task(String id, String name, Instant createdAt, Status status, String description) {
    this(
        id,
        name,
        createdAt,
        status,
        goalDescription(description),
        agentFeedback(description),
        null);
  }

  public Task(
      String id,
      String name,
      Instant createdAt,
      Status status,
      String description,
      String agentFeedback) {
    this(id, name, createdAt, status, description, agentFeedback, null);
  }

  public Task(
      String id,
      String name,
      Instant createdAt,
      Status status,
      String description,
      String agentFeedback,
      String failureDetail) {
    this.id = id;
    this.name = name;
    this.createdAt = createdAt;
    this.status = status;
    this.description = description;
    this.agentFeedback = agentFeedback;
    this.failureDetail = status == Status.failed ? normalizeFailureDetail(failureDetail) : null;
  }

  public static Task newTask(String name, String description) {
    return new Task(null, name, Instant.now(), Status.todo, description);
  }

  public static Task newTask(String name, Instant createdAt, String description) {
    return new Task(null, name, createdAt, Status.todo, description);
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Status getStatus() {
    return status;
  }

  public String getDescription() {
    return description;
  }

  public String getGoalDescription() {
    return description;
  }

  public Optional<String> getAgentFeedback() {
    return Optional.ofNullable(agentFeedback).filter(feedback -> !feedback.isBlank());
  }

  public Optional<String> getFailureDetail() {
    return Optional.ofNullable(failureDetail).filter(detail -> !detail.isBlank());
  }

  public Task withStatus(Status newStatus) {
    return new Task(id, name, createdAt, newStatus, description, agentFeedback, failureDetail);
  }

  public Task withFeedback(String feedback) {
    return new Task(id, name, createdAt, status, description, feedback.strip(), failureDetail);
  }

  public Task withFailureDetail(String detail) {
    return new Task(id, name, createdAt, status, description, agentFeedback, detail);
  }

  private static String goalDescription(String description) {
    int feedbackStart = description.indexOf(FEEDBACK_LABEL);
    return feedbackStart < 0
        ? description
        : description.substring(0, feedbackStart).stripTrailing();
  }

  private static String agentFeedback(String description) {
    int feedbackStart = description.lastIndexOf(FEEDBACK_LABEL);
    if (feedbackStart < 0) {
      return null;
    }
    String feedback = description.substring(feedbackStart + FEEDBACK_LABEL.length()).strip();
    return feedback.isEmpty() ? null : feedback;
  }

  private static String normalizeFailureDetail(String detail) {
    if (detail == null) {
      return null;
    }
    String normalized = detail.replaceAll("\\p{Cntrl}+", " ").replaceAll("\\s+", " ").strip();
    if (normalized.isEmpty()) {
      return null;
    }
    return normalized.length() <= MAX_FAILURE_DETAIL_LENGTH
        ? normalized
        : normalized.substring(0, MAX_FAILURE_DETAIL_LENGTH).stripTrailing();
  }

  @Override
  public String toString() {
    return "Task '" + name + "'";
  }
}
