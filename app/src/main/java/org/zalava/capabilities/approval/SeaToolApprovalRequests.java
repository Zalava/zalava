package org.zalava.capabilities.approval;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.zalava.api.InvocationContext;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.approval.adapter.out.filesystem.FileSystemApprovalRequestStore;
import org.zalava.capabilities.approval.application.port.out.ApprovalRequestStore;
import org.zalava.identity.accounts.domain.Actor;
import org.zalava.tasks.domain.ActorTaskExecutionReference;
import org.zalava.tasks.domain.ActorTaskReference;
import org.zalava.tasks.domain.TaskReference;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

@Component
public class SeaToolApprovalRequests {

  private static final int MAX_ENTRIES = 50;
  public static final String ACTOR_TASK_REFERENCE = "actorTaskReference";
  static final String POLICY_CHANNEL_ID = "permissionPolicyChannel";
  static final String POLICY_EXPIRES_AT = "permissionPolicyExpiresAt";
  static final String POLICY_MODULE_ID = "permissionPolicyModule";

  private final ArrayDeque<Entry> entries = new ArrayDeque<>();

  private final ApprovalRequestStore store;

  public SeaToolApprovalRequests() {
    store = null;
  }

  public SeaToolApprovalRequests(ApprovalRequestStore store) {
    this.store = store;
    loadEntries();
  }

  @Autowired
  public SeaToolApprovalRequests(@Value("${agent.workspace:Unknown}") Resource workspace)
      throws java.io.IOException {
    this(new FileSystemApprovalRequestStore(workspace.getFilePath()));
  }

  public synchronized Entry create(
      ZalavaProvider provider,
      ZalavaToolDescriptor tool,
      InvocationContext context,
      JsonNode arguments) {
    return create(provider, tool, context, arguments, null);
  }

  public synchronized Entry create(
      ZalavaProvider provider,
      ZalavaToolDescriptor tool,
      InvocationContext context,
      JsonNode arguments,
      TaskReference taskReference) {
    String argumentsJson = json(arguments);
    return entries.stream()
        .filter(entry -> entry.decision() == Decision.PENDING)
        .filter(
            entry ->
                entry.matches(
                    taskReference, provider.descriptor().providerId(), tool.name(), argumentsJson))
        .findFirst()
        .orElseGet(() -> add(provider, tool, context, arguments, argumentsJson, taskReference));
  }

  public synchronized Entry allowUnscoped(String requestId) {
    return decideUnscoped(requestId, Decision.ALLOWED, ApprovalScope.ONCE);
  }

  public synchronized Entry allowUnscopedTool(String requestId) {
    return decideUnscoped(requestId, Decision.ALLOWED, ApprovalScope.TOOL);
  }

  public synchronized Entry allow(String requestId, TaskReference taskReference) {
    return decide(requestId, taskReference, Decision.ALLOWED, ApprovalScope.ONCE);
  }

  public synchronized Entry allowTool(String requestId, TaskReference taskReference) {
    return decide(requestId, taskReference, Decision.ALLOWED, ApprovalScope.TOOL);
  }

  public synchronized Entry denyUnscoped(String requestId) {
    return decideUnscoped(requestId, Decision.DENIED, ApprovalScope.ONCE);
  }

  public synchronized Entry deny(String requestId, TaskReference taskReference) {
    return decide(requestId, taskReference, Decision.DENIED, ApprovalScope.ONCE);
  }

  public synchronized Entry revokeToolPolicy(String requestId) {
    Entry existing = find(requestId);
    if (existing.decision() != Decision.ALLOWED || existing.approvalScope() != ApprovalScope.TOOL) {
      throw new NotToolPolicyException(requestId);
    }
    return replace(
        existing,
        existing.withDecision(Decision.REVOKED, ApprovalScope.TOOL, Instant.now().toString()));
  }

  /** Restricts a durable policy without granting any scope or time it did not already have. */
  public synchronized Entry narrowToolPolicy(
      String requestId, Map<String, String> narrowedScope, Instant expiresAt) {
    Entry existing = find(requestId);
    if (!existing.isActiveToolPolicy()) throw new NotToolPolicyException(requestId);
    Map<String, String> requestedScope =
        narrowedScope == null ? Map.of() : Map.copyOf(narrowedScope);
    if (!requestedScope.entrySet().containsAll(existing.scope().entrySet())) {
      throw new PolicyNarrowingException(requestId, "cannot remove an existing scope constraint");
    }
    if (expiresAt != null) {
      if (!expiresAt.isAfter(Instant.now())) {
        throw new PolicyNarrowingException(requestId, "expiry must be in the future");
      }
      existing
          .expiresAt()
          .ifPresent(
              currentExpiry -> {
                if (expiresAt.isAfter(currentExpiry)) {
                  throw new PolicyNarrowingException(requestId, "cannot extend an existing expiry");
                }
              });
    }
    Map<String, String> attributes = new LinkedHashMap<>(existing.attributes());
    if (expiresAt != null) attributes.put(POLICY_EXPIRES_AT, expiresAt.toString());
    return replace(
        existing, existing.withPolicy(requestedScope, attributes, Instant.now().toString()));
  }

  public synchronized Optional<Entry> consumeDecision(
      TaskReference taskReference, String providerId, String toolName, JsonNode arguments) {
    String argumentsJson = json(arguments);
    return entries.stream()
        .filter(entry -> entry.consumedAt() == null)
        .filter(
            entry -> entry.decision() == Decision.ALLOWED || entry.decision() == Decision.DENIED)
        .filter(entry -> entry.matches(taskReference, providerId, toolName, argumentsJson))
        .findFirst()
        .map(entry -> replace(entry, entry.withConsumed(Instant.now().toString())));
  }

  public synchronized Optional<Entry> findUnscopedDecision(
      String actorId, String providerId, String toolName, JsonNode arguments) {
    String argumentsJson = json(arguments);
    return entries.stream()
        .filter(
            entry -> entry.decision() == Decision.ALLOWED || entry.decision() == Decision.DENIED)
        .filter(entry -> entry.matchesUnscoped(actorId, providerId, toolName, argumentsJson))
        .findFirst();
  }

  public synchronized Optional<Entry> findAllowedToolPolicy(
      String actorId, String providerId, String toolName, Map<String, String> providerScope) {
    return entries.stream()
        .filter(entry -> entry.decision() == Decision.ALLOWED)
        .filter(entry -> entry.approvalScope() == ApprovalScope.TOOL)
        .filter(entry -> entry.actorId().equals(actorId))
        .filter(entry -> entry.providerId().equals(providerId))
        .filter(entry -> entry.toolName().equals(toolName))
        .filter(entry -> entry.scope().equals(providerScope == null ? Map.of() : providerScope))
        .findFirst();
  }

  public synchronized Optional<Entry> findAllowedToolPolicy(
      InvocationContext context, ZalavaProvider provider, ZalavaToolDescriptor tool) {
    String channel = channel(context.attributes());
    return entries.stream()
        .filter(entry -> entry.matchesPolicy(context.actorId(), provider, tool, channel))
        .max(
            Comparator.comparingInt(Entry::policySpecificity)
                .thenComparing(Entry::decidedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(Entry::requestId));
  }

  public synchronized List<Entry> activeToolPolicies() {
    return entries.stream().filter(Entry::isActiveToolPolicy).toList();
  }

  public synchronized Entry get(String requestId) {
    return find(requestId);
  }

  public synchronized boolean hasPending(TaskReference taskReference) {
    return entries.stream()
        .anyMatch(
            entry -> entry.decision() == Decision.PENDING && entry.hasTaskReference(taskReference));
  }

  public synchronized List<Entry> pendingFor(TaskReference taskReference) {
    return entries.stream()
        .filter(entry -> entry.decision() == Decision.PENDING)
        .filter(entry -> entry.hasTaskReference(taskReference))
        .toList();
  }

  public synchronized List<Entry> unconsumedDecisionsFor(TaskReference taskReference) {
    return entries.stream()
        .filter(
            entry -> entry.decision() == Decision.ALLOWED || entry.decision() == Decision.DENIED)
        .filter(entry -> entry.consumedAt() == null)
        .filter(entry -> entry.hasTaskReference(taskReference))
        .toList();
  }

  public synchronized boolean hasPending(Actor actor, ActorTaskReference taskReference) {
    return entries.stream()
        .anyMatch(
            entry ->
                entry.decision() == Decision.PENDING
                    && matchesActorTask(entry, actor, taskReference));
  }

  public synchronized List<Entry> pendingFor(Actor actor, ActorTaskReference taskReference) {
    return entries.stream()
        .filter(entry -> entry.decision() == Decision.PENDING)
        .filter(entry -> matchesActorTask(entry, actor, taskReference))
        .toList();
  }

  public synchronized List<Entry> entriesFor(Actor actor, ActorTaskReference taskReference) {
    return entries.stream().filter(entry -> matchesActorTask(entry, actor, taskReference)).toList();
  }

  public synchronized List<Entry> unconsumedDecisionsFor(
      Actor actor, ActorTaskReference taskReference) {
    return entries.stream()
        .filter(
            entry -> entry.decision() == Decision.ALLOWED || entry.decision() == Decision.DENIED)
        .filter(entry -> entry.consumedAt() == null)
        .filter(entry -> matchesActorTask(entry, actor, taskReference))
        .toList();
  }

  public synchronized Entry get(Actor actor, ActorTaskReference taskReference, String requestId) {
    Entry entry = find(requestId);
    if (!matchesActorTask(entry, actor, taskReference)) throw new NotFoundException(requestId);
    return entry;
  }

  public synchronized List<Entry> recentEntries() {
    return List.copyOf(new ArrayList<>(entries));
  }

  public synchronized List<Entry> recentActorTaskEntries(Actor actor) {
    return entries.stream()
        .filter(entry -> actor.accountId().toString().equals(entry.actorId()))
        .filter(entry -> entry.attributes().containsKey(ACTOR_TASK_REFERENCE))
        .toList();
  }

  public synchronized void clear() {
    entries.clear();
    if (store != null) entries.forEach(entry -> store.delete(entry.requestId()));
  }

  private Entry add(
      ZalavaProvider provider,
      ZalavaToolDescriptor tool,
      InvocationContext context,
      JsonNode arguments,
      String argumentsJson,
      TaskReference taskReference) {
    Map<String, String> attributes = new LinkedHashMap<>(context.attributes());
    attributes.put(POLICY_CHANNEL_ID, channel(context.attributes()));
    attributes.put(POLICY_MODULE_ID, provider.descriptor().moduleId());
    Entry entry =
        new Entry(
            UUID.randomUUID().toString(),
            Instant.now().toString(),
            null,
            null,
            taskReference == null ? null : taskReference.path(),
            provider.descriptor().providerId(),
            tool.name(),
            context.actorId(),
            attributes,
            provider.descriptor().scope(),
            tool.policyTags(),
            tool.sideEffecting(),
            arguments.deepCopy(),
            argumentsJson,
            RequestSummary.from(
                provider.descriptor().providerId(),
                tool.name(),
                context.actorId(),
                provider.descriptor().scope(),
                tool.policyTags(),
                tool.sideEffecting(),
                argumentsJson,
                provider.descriptor().moduleId(),
                attributes.get(POLICY_CHANNEL_ID)),
            Decision.PENDING,
            ApprovalScope.ONCE);
    entries.addFirst(entry);
    persist(entry);
    while (entries.size() > MAX_ENTRIES) {
      delete(entries.removeLast());
    }
    return entry;
  }

  private Entry decide(
      String requestId,
      TaskReference taskReference,
      Decision decision,
      ApprovalScope approvalScope) {
    Entry existing = find(requestId);
    if (!existing.hasTaskReference(taskReference)) {
      throw new NotFoundException(requestId);
    }
    return decide(existing, decision, approvalScope);
  }

  private Entry decideUnscoped(String requestId, Decision decision, ApprovalScope approvalScope) {
    Entry existing = find(requestId);
    if (existing.taskReference() != null) {
      throw new WrongScopeException(requestId);
    }
    return decide(existing, decision, approvalScope);
  }

  private Entry decide(Entry existing, Decision decision, ApprovalScope approvalScope) {
    if (existing.decision() != Decision.PENDING) {
      throw new AlreadyDecidedException(existing.requestId());
    }
    return replace(
        existing, existing.withDecision(decision, approvalScope, Instant.now().toString()));
  }

  private Entry find(String requestId) {
    return entries.stream()
        .filter(entry -> entry.requestId().equals(requestId))
        .findFirst()
        .orElseThrow(() -> new NotFoundException(requestId));
  }

  private Entry replace(Entry existing, Entry replacement) {
    entries.remove(existing);
    entries.addFirst(replacement);
    persist(replacement);
    return replacement;
  }

  private void loadEntries() {
    if (store == null) return;
    List<Entry> loaded = store.load();
    loaded.stream().limit(MAX_ENTRIES).forEach(entries::addLast);
    loaded.stream().skip(MAX_ENTRIES).forEach(entry -> store.delete(entry.requestId()));
  }

  private void persist(Entry entry) {
    if (store != null) store.save(entry);
  }

  private void delete(Entry entry) {
    if (store != null) store.delete(entry.requestId());
  }

  private static String channel(Map<String, String> attributes) {
    String channel = attributes.get(POLICY_CHANNEL_ID);
    return channel == null || channel.isBlank() ? "unknown" : channel;
  }

  private static String json(JsonNode arguments) {
    try {
      return new tools.jackson.databind.ObjectMapper().writeValueAsString(arguments);
    } catch (JacksonException ex) {
      throw new IllegalArgumentException(
          "Unable to retain SEA tool approval request arguments", ex);
    }
  }

  private static boolean matchesActorTask(
      Entry entry, Actor actor, ActorTaskReference taskReference) {
    String encoded = new ActorTaskExecutionReference(actor, taskReference).encode();
    return actor.accountId().toString().equals(entry.actorId())
        && encoded.equals(entry.attributes().get(ACTOR_TASK_REFERENCE));
  }

  public enum Decision {
    PENDING,
    ALLOWED,
    DENIED,
    REVOKED
  }

  public enum ApprovalScope {
    ONCE,
    TOOL
  }

  public record Entry(
      String requestId,
      String createdAt,
      String decidedAt,
      String consumedAt,
      String taskReference,
      String providerId,
      String toolName,
      String actorId,
      Map<String, String> attributes,
      Map<String, String> scope,
      List<String> policyTags,
      boolean sideEffecting,
      JsonNode arguments,
      String argumentsJson,
      RequestSummary summary,
      Decision decision,
      ApprovalScope approvalScope) {
    public Entry {
      attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
      scope = scope == null ? Map.of() : Map.copyOf(scope);
      policyTags = policyTags == null ? List.of() : List.copyOf(policyTags);
      approvalScope = approvalScope == null ? ApprovalScope.ONCE : approvalScope;
      summary =
          summary == null
              ? RequestSummary.from(
                  providerId, toolName, actorId, scope, policyTags, sideEffecting, argumentsJson)
              : summary;
    }

    boolean hasTaskReference(TaskReference reference) {
      return reference != null && reference.path().equals(taskReference);
    }

    boolean matches(
        TaskReference reference, String providerId, String toolName, String argumentsJson) {
      return hasTaskReference(reference)
          && this.providerId.equals(providerId)
          && this.toolName.equals(toolName)
          && this.argumentsJson.equals(argumentsJson);
    }

    boolean matchesUnscoped(
        String actorId, String providerId, String toolName, String argumentsJson) {
      return taskReference == null
          && this.actorId.equals(actorId)
          && this.providerId.equals(providerId)
          && this.toolName.equals(toolName)
          && this.argumentsJson.equals(argumentsJson);
    }

    boolean isActiveToolPolicy() {
      if (decision != Decision.ALLOWED || approvalScope != ApprovalScope.TOOL) return false;
      String expiresAt = attributes.get(POLICY_EXPIRES_AT);
      if (expiresAt == null || expiresAt.isBlank()) return true;
      try {
        return Instant.now().isBefore(Instant.parse(expiresAt));
      } catch (RuntimeException exception) {
        return false;
      }
    }

    Optional<Instant> expiresAt() {
      String expiresAt = attributes.get(POLICY_EXPIRES_AT);
      if (expiresAt == null || expiresAt.isBlank()) return Optional.empty();
      try {
        return Optional.of(Instant.parse(expiresAt));
      } catch (RuntimeException exception) {
        return Optional.empty();
      }
    }

    boolean matchesPolicy(
        String actorId, ZalavaProvider provider, ZalavaToolDescriptor tool, String channel) {
      return isActiveToolPolicy()
          && this.actorId.equals(actorId)
          && this.providerId.equals(provider.descriptor().providerId())
          && this.toolName.equals(tool.name())
          && provider.descriptor().scope().entrySet().containsAll(this.scope.entrySet())
          && provider.descriptor().moduleId().equals(attributes.get(POLICY_MODULE_ID))
          && channel.equals(attributes.get(POLICY_CHANNEL_ID));
    }

    int policySpecificity() {
      return scope.size();
    }

    Entry withDecision(Decision decision, ApprovalScope approvalScope, String decidedAt) {
      return new Entry(
          requestId,
          createdAt,
          decidedAt,
          consumedAt,
          taskReference,
          providerId,
          toolName,
          actorId,
          attributes,
          scope,
          policyTags,
          sideEffecting,
          arguments,
          argumentsJson,
          summary,
          decision,
          approvalScope);
    }

    Entry withConsumed(String consumedAt) {
      return new Entry(
          requestId,
          createdAt,
          decidedAt,
          consumedAt,
          taskReference,
          providerId,
          toolName,
          actorId,
          attributes,
          scope,
          policyTags,
          sideEffecting,
          arguments,
          argumentsJson,
          summary,
          decision,
          approvalScope);
    }

    Entry withPolicy(Map<String, String> scope, Map<String, String> attributes, String decidedAt) {
      return new Entry(
          requestId,
          createdAt,
          decidedAt,
          consumedAt,
          taskReference,
          providerId,
          toolName,
          actorId,
          attributes,
          scope,
          policyTags,
          sideEffecting,
          arguments,
          argumentsJson,
          summary,
          decision,
          approvalScope);
    }
  }

  public record RequestSummary(
      String prompt,
      String effect,
      String argumentsPreview,
      String allowOnceDecision,
      String allowToolPolicy,
      String denyDecision,
      Map<String, String> scope,
      List<String> policyTags) {
    private static final int MAX_ARGUMENTS_PREVIEW = 1_000;

    public RequestSummary {
      scope = scope == null ? Map.of() : Map.copyOf(scope);
      policyTags = policyTags == null ? List.of() : List.copyOf(policyTags);
    }

    static RequestSummary from(
        String providerId,
        String toolName,
        String actorId,
        Map<String, String> scope,
        List<String> policyTags,
        boolean sideEffecting,
        String argumentsJson) {
      return from(
          providerId,
          toolName,
          actorId,
          scope,
          policyTags,
          sideEffecting,
          argumentsJson,
          null,
          null);
    }

    static RequestSummary from(
        String providerId,
        String toolName,
        String actorId,
        Map<String, String> scope,
        List<String> policyTags,
        boolean sideEffecting,
        String argumentsJson,
        String moduleId,
        String channel) {
      String effect = sideEffecting ? "side-effecting" : "read-only";
      String safeProviderId = providerId == null ? "unknown-provider" : providerId;
      String safeToolName = toolName == null ? "unknown-tool" : toolName;
      String safeActorId = actorId == null ? "unknown-actor" : actorId;
      String safeModuleId = moduleId == null || moduleId.isBlank() ? "unknown-module" : moduleId;
      String safeChannel = channel == null || channel.isBlank() ? "unknown-channel" : channel;
      return new RequestSummary(
          "SEA requests approval to run %s on provider %s from module %s for actor %s via %s."
              .formatted(safeToolName, safeProviderId, safeModuleId, safeActorId, safeChannel),
          effect,
          preview(argumentsJson),
          "Allow this exact request once.",
          "Always allow actor %s to run tool %s on provider %s from module %s via %s without another prompt."
              .formatted(safeActorId, safeToolName, safeProviderId, safeModuleId, safeChannel),
          "Deny this request and do not run the tool.",
          scope,
          policyTags);
    }

    private static String preview(String argumentsJson) {
      if (argumentsJson == null || argumentsJson.isBlank()) {
        return "{}";
      }
      String normalized = argumentsJson.replaceAll("\\s+", " ").trim();
      return normalized.length() <= MAX_ARGUMENTS_PREVIEW
          ? normalized
          : normalized.substring(0, MAX_ARGUMENTS_PREVIEW) + "...";
    }
  }

  public static final class NotFoundException extends RuntimeException {
    public NotFoundException(String requestId) {
      super("SEA tool approval request not found: " + requestId);
    }
  }

  public static final class AlreadyDecidedException extends RuntimeException {
    public AlreadyDecidedException(String requestId) {
      super("SEA tool approval request already decided: " + requestId);
    }
  }

  public static final class WrongScopeException extends RuntimeException {
    public WrongScopeException(String requestId) {
      super("SEA tool approval request must be decided from its job: " + requestId);
    }
  }

  public static final class NotToolPolicyException extends RuntimeException {
    public NotToolPolicyException(String requestId) {
      super("SEA tool approval request is not an active tool policy: " + requestId);
    }
  }

  public static final class PolicyNarrowingException extends RuntimeException {
    public PolicyNarrowingException(String requestId, String reason) {
      super("SEA policy can only be narrowed: " + requestId + " (" + reason + ")");
    }
  }
}
