package org.zalava.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.zalava.InvocationContext;
import org.zalava.ProviderCapabilities;
import org.zalava.ProviderDescriptor;
import org.zalava.SeaProvider;
import org.zalava.SeaToolDescriptor;
import org.zalava.approval.adapter.out.filesystem.FileSystemApprovalRequestStore;
import tools.jackson.databind.ObjectMapper;

class SeaToolApprovalRequestsTest {

  @TempDir Path workspace;
  private SeaToolApprovalRequests requests;
  private static final ObjectMapper JSON = new ObjectMapper();

  @BeforeEach
  void setUp() {
    var store = new FileSystemApprovalRequestStore(workspace);
    requests = new SeaToolApprovalRequests(store);
  }

  private String addPending(String provider, String tool, String actor) {
    var entry =
        new SeaToolApprovalRequests.Entry(
            UUID.randomUUID().toString(),
            "2025-01-01T00:00:00Z",
            null,
            null,
            null,
            provider,
            tool,
            actor,
            Map.of(),
            Map.of(),
            List.of(),
            false,
            JSON.createObjectNode(),
            "{}",
            null,
            SeaToolApprovalRequests.Decision.PENDING,
            SeaToolApprovalRequests.ApprovalScope.ONCE);
    // Add via store and reload
    new FileSystemApprovalRequestStore(workspace).save(entry);
    return entry.requestId();
  }

  @Test
  void denyUnscopedDecidesEntry() {
    var fresh = new SeaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    String id = addPending("p", "t", "a");
    var reloaded = new SeaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    var denied = reloaded.denyUnscoped(id);
    assertThat(denied.decision()).isEqualTo(SeaToolApprovalRequests.Decision.DENIED);
  }

  @Test
  void allowUnscopedDecidesEntry() {
    String id = addPending("p", "t", "a");
    var fresh = new SeaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    var allowed = fresh.allowUnscoped(id);
    assertThat(allowed.decision()).isEqualTo(SeaToolApprovalRequests.Decision.ALLOWED);
    assertThat(allowed.approvalScope()).isEqualTo(SeaToolApprovalRequests.ApprovalScope.ONCE);
  }

  @Test
  void allowUnscopedToolDecidesAsToolPolicy() {
    String id = addPending("p", "t", "a");
    var fresh = new SeaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    var allowed = fresh.allowUnscopedTool(id);
    assertThat(allowed.decision()).isEqualTo(SeaToolApprovalRequests.Decision.ALLOWED);
    assertThat(allowed.approvalScope()).isEqualTo(SeaToolApprovalRequests.ApprovalScope.TOOL);
  }

  @Test
  void alreadyDecidedThrows() {
    String id = addPending("p", "t", "a");
    var fresh = new SeaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    fresh.denyUnscoped(id);
    assertThatThrownBy(() -> fresh.denyUnscoped(id))
        .isInstanceOf(SeaToolApprovalRequests.AlreadyDecidedException.class);
  }

  @Test
  void wrongScopeThrows() {
    var entry =
        new SeaToolApprovalRequests.Entry(
            UUID.randomUUID().toString(),
            "2025-01-01T00:00:00Z",
            null,
            null,
            "job-1",
            "p",
            "t",
            "a",
            Map.of(),
            Map.of(),
            List.of(),
            false,
            JSON.createObjectNode(),
            "{}",
            null,
            SeaToolApprovalRequests.Decision.PENDING,
            SeaToolApprovalRequests.ApprovalScope.ONCE);
    new FileSystemApprovalRequestStore(workspace).save(entry);
    var fresh = new SeaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    assertThatThrownBy(() -> fresh.denyUnscoped(entry.requestId()))
        .isInstanceOf(SeaToolApprovalRequests.WrongScopeException.class);
  }

  @Test
  void missingRequestThrows() {
    assertThatThrownBy(() -> requests.denyUnscoped(UUID.randomUUID().toString()))
        .isInstanceOf(SeaToolApprovalRequests.NotFoundException.class);
  }

  @Test
  void getThrowsForMissingId() {
    assertThatThrownBy(() -> requests.get(UUID.randomUUID().toString()))
        .isInstanceOf(SeaToolApprovalRequests.NotFoundException.class);
  }

  @Test
  void recentEntriesReturnsEmptyInitially() {
    assertThat(requests.recentEntries()).isEmpty();
  }

  @Test
  void activeToolPoliciesReturnsAllowedToolScope() {
    String id = addPending("p", "t", "a");
    var fresh = new SeaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    fresh.allowUnscopedTool(id);
    assertThat(fresh.activeToolPolicies()).hasSize(1);
  }

  @Test
  void activeToolPoliciesEmptyForOnceScope() {
    String id = addPending("p", "t", "a");
    var fresh = new SeaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    fresh.allowUnscoped(id);
    assertThat(fresh.activeToolPolicies()).isEmpty();
  }

  @Test
  void revokeToolPolicyDecidesRevoked() {
    String id = addPending("p", "t", "a");
    var fresh = new SeaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    fresh.allowUnscopedTool(id);
    var revoked = fresh.revokeToolPolicy(id);
    assertThat(revoked.decision()).isEqualTo(SeaToolApprovalRequests.Decision.REVOKED);
  }

  @Test
  void revokeToolPolicyThrowsForNonToolPolicy() {
    String id = addPending("p", "t", "a");
    var fresh = new SeaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    fresh.allowUnscoped(id);
    assertThatThrownBy(() -> fresh.revokeToolPolicy(id))
        .isInstanceOf(SeaToolApprovalRequests.NotToolPolicyException.class);
  }

  @Test
  void requestSummaryGeneratesPromptForNullProvider() {
    var entry =
        new SeaToolApprovalRequests.Entry(
            UUID.randomUUID().toString(),
            "2025-01-01T00:00:00Z",
            null,
            null,
            null,
            null,
            "t",
            "a",
            Map.of(),
            Map.of(),
            List.of(),
            false,
            JSON.createObjectNode(),
            "{}",
            null,
            SeaToolApprovalRequests.Decision.PENDING,
            SeaToolApprovalRequests.ApprovalScope.ONCE);
    var summary = entry.summary();
    assertThat(summary.prompt()).contains("unknown-provider");
  }

  @Test
  void requestSummaryTruncatesLongArgumentsJson() {
    String longArgs = "x".repeat(2000);
    var entry =
        new SeaToolApprovalRequests.Entry(
            UUID.randomUUID().toString(),
            "2025-01-01T00:00:00Z",
            null,
            null,
            null,
            "p",
            "t",
            "a",
            Map.of(),
            Map.of(),
            List.of(),
            false,
            null,
            longArgs,
            null,
            SeaToolApprovalRequests.Decision.PENDING,
            SeaToolApprovalRequests.ApprovalScope.ONCE);
    var summary = entry.summary();
    assertThat(summary.argumentsPreview()).endsWith("...");
  }

  @Test
  void requestSummarySideEffecting() {
    var entry =
        new SeaToolApprovalRequests.Entry(
            UUID.randomUUID().toString(),
            "2025-01-01T00:00:00Z",
            null,
            null,
            null,
            "p",
            "t",
            "a",
            Map.of(),
            Map.of(),
            List.of(),
            true,
            JSON.createObjectNode(),
            "{}",
            null,
            SeaToolApprovalRequests.Decision.PENDING,
            SeaToolApprovalRequests.ApprovalScope.ONCE);
    assertThat(entry.summary().effect()).isEqualTo("side-effecting");
  }

  @Test
  void requestSummaryReadOnly() {
    var entry =
        new SeaToolApprovalRequests.Entry(
            UUID.randomUUID().toString(),
            "2025-01-01T00:00:00Z",
            null,
            null,
            null,
            "p",
            "t",
            "a",
            Map.of(),
            Map.of(),
            List.of(),
            false,
            JSON.createObjectNode(),
            "{}",
            null,
            SeaToolApprovalRequests.Decision.PENDING,
            SeaToolApprovalRequests.ApprovalScope.ONCE);
    assertThat(entry.summary().effect()).isEqualTo("read-only");
  }

  @Test
  void durableToolPolicyRequiresTheSameActorChannelAndModule() {
    SeaProvider provider = provider("module-one");
    SeaToolDescriptor tool = new SeaToolDescriptor("write", "Writes data", true);
    InvocationContext telegramActor =
        new InvocationContext(
            "actor-one", false, Map.of(SeaToolApprovalRequests.POLICY_CHANNEL_ID, "telegram"));
    SeaToolApprovalRequests.Entry request =
        requests.create(provider, tool, telegramActor, JSON.createObjectNode());
    String requestId = request.requestId();
    assertThat(request.summary().prompt()).contains("module-one", "telegram");
    assertThat(request.summary().allowToolPolicy()).contains("module-one", "telegram");
    requests.allowUnscopedTool(requestId);

    assertThat(requests.findAllowedToolPolicy(telegramActor, provider, tool)).isPresent();
    assertThat(
            requests.findAllowedToolPolicy(
                new InvocationContext(
                    "actor-one", false, Map.of(SeaToolApprovalRequests.POLICY_CHANNEL_ID, "web")),
                provider,
                tool))
        .isEmpty();
    assertThat(
            requests.findAllowedToolPolicy(
                new InvocationContext(
                    "actor-two",
                    false,
                    Map.of(SeaToolApprovalRequests.POLICY_CHANNEL_ID, "telegram")),
                provider,
                tool))
        .isEmpty();
    assertThat(requests.findAllowedToolPolicy(telegramActor, provider("module-two"), tool))
        .isEmpty();
  }

  @Test
  void revokedDurablePolicyDoesNotMatchAfterReload() {
    SeaProvider provider = provider("module-one");
    SeaToolDescriptor tool = new SeaToolDescriptor("write", "Writes data", true);
    InvocationContext context =
        new InvocationContext(
            "actor-one", false, Map.of(SeaToolApprovalRequests.POLICY_CHANNEL_ID, "telegram"));

    String requestId =
        requests.create(provider, tool, context, JSON.createObjectNode()).requestId();
    requests.allowUnscopedTool(requestId);

    SeaToolApprovalRequests reloaded =
        new SeaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    assertThat(reloaded.findAllowedToolPolicy(context, provider, tool)).isPresent();

    reloaded.revokeToolPolicy(requestId);

    SeaToolApprovalRequests reloadedAfterRevocation =
        new SeaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    assertThat(reloadedAfterRevocation.findAllowedToolPolicy(context, provider, tool)).isEmpty();
    assertThat(reloadedAfterRevocation.activeToolPolicies()).isEmpty();
  }

  @Test
  void expiredAndMalformedDurablePolicyExpiryFailsClosedAfterReload() {
    SeaProvider provider = provider("module-one");
    SeaToolDescriptor tool = new SeaToolDescriptor("write", "Writes data", true);
    InvocationContext expired =
        new InvocationContext(
            "actor-one",
            false,
            Map.of(
                SeaToolApprovalRequests.POLICY_CHANNEL_ID,
                "telegram",
                SeaToolApprovalRequests.POLICY_EXPIRES_AT,
                Instant.now().minusSeconds(1).toString()));
    String expiredId =
        requests.create(provider, tool, expired, JSON.createObjectNode()).requestId();
    requests.allowUnscopedTool(expiredId);

    InvocationContext malformed =
        new InvocationContext(
            "actor-two",
            false,
            Map.of(
                SeaToolApprovalRequests.POLICY_CHANNEL_ID,
                "telegram",
                SeaToolApprovalRequests.POLICY_EXPIRES_AT,
                "not-an-instant"));
    String malformedId =
        requests.create(provider, tool, malformed, JSON.createObjectNode()).requestId();
    requests.allowUnscopedTool(malformedId);

    SeaToolApprovalRequests reloaded =
        new SeaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));

    assertThat(reloaded.findAllowedToolPolicy(expired, provider, tool)).isEmpty();
    assertThat(reloaded.findAllowedToolPolicy(malformed, provider, tool)).isEmpty();
    assertThat(reloaded.activeToolPolicies()).isEmpty();
  }

  @Test
  void selectsTheNarrowestMatchingPolicyDeterministically() {
    SeaProvider provider = provider("module-one", Map.of("root", "workspace", "tenant", "home"));
    SeaToolDescriptor tool = new SeaToolDescriptor("write", "Writes data", true);
    InvocationContext context =
        new InvocationContext(
            "actor-one", false, Map.of(SeaToolApprovalRequests.POLICY_CHANNEL_ID, "telegram"));
    FileSystemApprovalRequestStore store = new FileSystemApprovalRequestStore(workspace);
    store.save(
        policyEntry("00000000-0000-0000-0000-000000000001", provider, tool, context, Map.of()));
    store.save(
        policyEntry(
            "00000000-0000-0000-0000-000000000002",
            provider,
            tool,
            context,
            Map.of("root", "workspace")));

    SeaToolApprovalRequests reloaded = new SeaToolApprovalRequests(store);

    assertThat(reloaded.findAllowedToolPolicy(context, provider, tool))
        .map(SeaToolApprovalRequests.Entry::requestId)
        .contains("00000000-0000-0000-0000-000000000002");
  }

  @Test
  void narrowsAnActivePolicyWithoutAllowingScopeOrExpiryWidening() {
    SeaProvider provider = provider("module-one", Map.of("root", "workspace", "tenant", "home"));
    SeaToolDescriptor tool = new SeaToolDescriptor("write", "Writes data", true);
    InvocationContext context =
        new InvocationContext(
            "actor-one", false, Map.of(SeaToolApprovalRequests.POLICY_CHANNEL_ID, "telegram"));
    FileSystemApprovalRequestStore store = new FileSystemApprovalRequestStore(workspace);
    String requestId = "00000000-0000-0000-0000-000000000003";
    store.save(policyEntry(requestId, provider, tool, context, Map.of()));
    SeaToolApprovalRequests managed = new SeaToolApprovalRequests(store);
    Instant expiry = Instant.now().plusSeconds(60);

    SeaToolApprovalRequests.Entry narrowed =
        managed.narrowToolPolicy(requestId, Map.of("root", "workspace"), expiry);

    assertThat(narrowed.scope()).containsExactly(Map.entry("root", "workspace"));
    assertThat(narrowed.attributes())
        .containsEntry(SeaToolApprovalRequests.POLICY_EXPIRES_AT, expiry.toString());
    assertThat(managed.findAllowedToolPolicy(context, provider, tool)).isPresent();
    assertThatThrownBy(() -> managed.narrowToolPolicy(requestId, Map.of(), null))
        .isInstanceOf(SeaToolApprovalRequests.PolicyNarrowingException.class);
    assertThatThrownBy(
            () ->
                managed.narrowToolPolicy(
                    requestId, Map.of("root", "workspace"), expiry.plusSeconds(1)))
        .isInstanceOf(SeaToolApprovalRequests.PolicyNarrowingException.class);
  }

  @Test
  void clearRemovesAllEntries() {
    addPending("p", "t", "a");
    var fresh = new SeaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    fresh.clear();
    assertThat(fresh.recentEntries()).isEmpty();
  }

  private static SeaToolApprovalRequests.Entry policyEntry(
      String requestId,
      SeaProvider provider,
      SeaToolDescriptor tool,
      InvocationContext context,
      Map<String, String> scope) {
    return new SeaToolApprovalRequests.Entry(
        requestId,
        "2026-01-01T00:00:00Z",
        "2026-01-01T00:00:00Z",
        null,
        null,
        provider.descriptor().providerId(),
        tool.name(),
        context.actorId(),
        Map.of(
            SeaToolApprovalRequests.POLICY_CHANNEL_ID,
            "telegram",
            SeaToolApprovalRequests.POLICY_MODULE_ID,
            provider.descriptor().moduleId()),
        scope,
        List.of(),
        true,
        JSON.createObjectNode(),
        "{}",
        null,
        SeaToolApprovalRequests.Decision.ALLOWED,
        SeaToolApprovalRequests.ApprovalScope.TOOL);
  }

  private static SeaProvider provider(String moduleId) {
    return provider(moduleId, Map.of());
  }

  private static SeaProvider provider(String moduleId, Map<String, String> scope) {
    return new SeaProvider() {
      @Override
      public ProviderDescriptor descriptor() {
        return new ProviderDescriptor(
            "test-provider",
            moduleId,
            "test",
            "Test provider",
            "Test provider",
            "1",
            ProviderCapabilities.toolsOnly(),
            List.of(),
            scope);
      }

      @Override
      public ProviderCapabilities capabilities() {
        return ProviderCapabilities.toolsOnly();
      }

      @Override
      public List<SeaToolDescriptor> listTools() {
        return List.of();
      }
    };
  }
}
