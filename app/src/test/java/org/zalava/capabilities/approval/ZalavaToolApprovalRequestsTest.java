package org.zalava.capabilities.approval;

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
import org.zalava.api.InvocationContext;
import org.zalava.api.ProviderCapabilities;
import org.zalava.api.ProviderDescriptor;
import org.zalava.api.ZalavaProvider;
import org.zalava.api.ZalavaToolDescriptor;
import org.zalava.capabilities.approval.adapter.out.filesystem.FileSystemApprovalRequestStore;
import tools.jackson.databind.ObjectMapper;

class ZalavaToolApprovalRequestsTest {

  @TempDir Path workspace;
  private ZalavaToolApprovalRequests requests;
  private static final ObjectMapper JSON = new ObjectMapper();

  @BeforeEach
  void setUp() {
    var store = new FileSystemApprovalRequestStore(workspace);
    requests = new ZalavaToolApprovalRequests(store);
  }

  private String addPending(String provider, String tool, String actor) {
    var entry =
        new ZalavaToolApprovalRequests.Entry(
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
            ZalavaToolApprovalRequests.Decision.PENDING,
            ZalavaToolApprovalRequests.ApprovalScope.ONCE);
    // Add via store and reload
    new FileSystemApprovalRequestStore(workspace).save(entry);
    return entry.requestId();
  }

  @Test
  void denyUnscopedDecidesEntry() {
    var fresh = new ZalavaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    String id = addPending("p", "t", "a");
    var reloaded = new ZalavaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    var denied = reloaded.denyUnscoped(id);
    assertThat(denied.decision()).isEqualTo(ZalavaToolApprovalRequests.Decision.DENIED);
  }

  @Test
  void allowUnscopedDecidesEntry() {
    String id = addPending("p", "t", "a");
    var fresh = new ZalavaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    var allowed = fresh.allowUnscoped(id);
    assertThat(allowed.decision()).isEqualTo(ZalavaToolApprovalRequests.Decision.ALLOWED);
    assertThat(allowed.approvalScope()).isEqualTo(ZalavaToolApprovalRequests.ApprovalScope.ONCE);
  }

  @Test
  void allowUnscopedToolDecidesAsToolPolicy() {
    String id = addPending("p", "t", "a");
    var fresh = new ZalavaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    var allowed = fresh.allowUnscopedTool(id);
    assertThat(allowed.decision()).isEqualTo(ZalavaToolApprovalRequests.Decision.ALLOWED);
    assertThat(allowed.approvalScope()).isEqualTo(ZalavaToolApprovalRequests.ApprovalScope.TOOL);
  }

  @Test
  void alreadyDecidedThrows() {
    String id = addPending("p", "t", "a");
    var fresh = new ZalavaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    fresh.denyUnscoped(id);
    assertThatThrownBy(() -> fresh.denyUnscoped(id))
        .isInstanceOf(ZalavaToolApprovalRequests.AlreadyDecidedException.class);
  }

  @Test
  void wrongScopeThrows() {
    var entry =
        new ZalavaToolApprovalRequests.Entry(
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
            ZalavaToolApprovalRequests.Decision.PENDING,
            ZalavaToolApprovalRequests.ApprovalScope.ONCE);
    new FileSystemApprovalRequestStore(workspace).save(entry);
    var fresh = new ZalavaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    assertThatThrownBy(() -> fresh.denyUnscoped(entry.requestId()))
        .isInstanceOf(ZalavaToolApprovalRequests.WrongScopeException.class);
  }

  @Test
  void missingRequestThrows() {
    assertThatThrownBy(() -> requests.denyUnscoped(UUID.randomUUID().toString()))
        .isInstanceOf(ZalavaToolApprovalRequests.NotFoundException.class);
  }

  @Test
  void getThrowsForMissingId() {
    assertThatThrownBy(() -> requests.get(UUID.randomUUID().toString()))
        .isInstanceOf(ZalavaToolApprovalRequests.NotFoundException.class);
  }

  @Test
  void recentEntriesReturnsEmptyInitially() {
    assertThat(requests.recentEntries()).isEmpty();
  }

  @Test
  void activeToolPoliciesReturnsAllowedToolScope() {
    String id = addPending("p", "t", "a");
    var fresh = new ZalavaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    fresh.allowUnscopedTool(id);
    assertThat(fresh.activeToolPolicies()).hasSize(1);
  }

  @Test
  void activeToolPoliciesEmptyForOnceScope() {
    String id = addPending("p", "t", "a");
    var fresh = new ZalavaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    fresh.allowUnscoped(id);
    assertThat(fresh.activeToolPolicies()).isEmpty();
  }

  @Test
  void revokeToolPolicyDecidesRevoked() {
    String id = addPending("p", "t", "a");
    var fresh = new ZalavaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    fresh.allowUnscopedTool(id);
    var revoked = fresh.revokeToolPolicy(id);
    assertThat(revoked.decision()).isEqualTo(ZalavaToolApprovalRequests.Decision.REVOKED);
  }

  @Test
  void revokeToolPolicyThrowsForNonToolPolicy() {
    String id = addPending("p", "t", "a");
    var fresh = new ZalavaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    fresh.allowUnscoped(id);
    assertThatThrownBy(() -> fresh.revokeToolPolicy(id))
        .isInstanceOf(ZalavaToolApprovalRequests.NotToolPolicyException.class);
  }

  @Test
  void requestSummaryGeneratesPromptForNullProvider() {
    var entry =
        new ZalavaToolApprovalRequests.Entry(
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
            ZalavaToolApprovalRequests.Decision.PENDING,
            ZalavaToolApprovalRequests.ApprovalScope.ONCE);
    var summary = entry.summary();
    assertThat(summary.prompt()).contains("unknown-provider");
  }

  @Test
  void requestSummaryTruncatesLongArgumentsJson() {
    String longArgs = "x".repeat(2000);
    var entry =
        new ZalavaToolApprovalRequests.Entry(
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
            ZalavaToolApprovalRequests.Decision.PENDING,
            ZalavaToolApprovalRequests.ApprovalScope.ONCE);
    var summary = entry.summary();
    assertThat(summary.argumentsPreview()).endsWith("...");
  }

  @Test
  void requestSummarySideEffecting() {
    var entry =
        new ZalavaToolApprovalRequests.Entry(
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
            ZalavaToolApprovalRequests.Decision.PENDING,
            ZalavaToolApprovalRequests.ApprovalScope.ONCE);
    assertThat(entry.summary().effect()).isEqualTo("side-effecting");
  }

  @Test
  void requestSummaryReadOnly() {
    var entry =
        new ZalavaToolApprovalRequests.Entry(
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
            ZalavaToolApprovalRequests.Decision.PENDING,
            ZalavaToolApprovalRequests.ApprovalScope.ONCE);
    assertThat(entry.summary().effect()).isEqualTo("read-only");
  }

  @Test
  void durableToolPolicyRequiresTheSameActorChannelAndModule() {
    ZalavaProvider provider = provider("module-one");
    ZalavaToolDescriptor tool = new ZalavaToolDescriptor("write", "Writes data", true);
    InvocationContext telegramActor =
        new InvocationContext(
            "actor-one", false, Map.of(ZalavaToolApprovalRequests.POLICY_CHANNEL_ID, "telegram"));
    ZalavaToolApprovalRequests.Entry request =
        requests.create(provider, tool, telegramActor, JSON.createObjectNode());
    String requestId = request.requestId();
    assertThat(request.summary().prompt()).contains("module-one", "telegram");
    assertThat(request.summary().allowToolPolicy()).contains("module-one", "telegram");
    requests.allowUnscopedTool(requestId);

    assertThat(requests.findAllowedToolPolicy(telegramActor, provider, tool)).isPresent();
    assertThat(
            requests.findAllowedToolPolicy(
                new InvocationContext(
                    "actor-one",
                    false,
                    Map.of(ZalavaToolApprovalRequests.POLICY_CHANNEL_ID, "web")),
                provider,
                tool))
        .isEmpty();
    assertThat(
            requests.findAllowedToolPolicy(
                new InvocationContext(
                    "actor-two",
                    false,
                    Map.of(ZalavaToolApprovalRequests.POLICY_CHANNEL_ID, "telegram")),
                provider,
                tool))
        .isEmpty();
    assertThat(requests.findAllowedToolPolicy(telegramActor, provider("module-two"), tool))
        .isEmpty();
  }

  @Test
  void revokedDurablePolicyDoesNotMatchAfterReload() {
    ZalavaProvider provider = provider("module-one");
    ZalavaToolDescriptor tool = new ZalavaToolDescriptor("write", "Writes data", true);
    InvocationContext context =
        new InvocationContext(
            "actor-one", false, Map.of(ZalavaToolApprovalRequests.POLICY_CHANNEL_ID, "telegram"));

    String requestId =
        requests.create(provider, tool, context, JSON.createObjectNode()).requestId();
    requests.allowUnscopedTool(requestId);

    ZalavaToolApprovalRequests reloaded =
        new ZalavaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    assertThat(reloaded.findAllowedToolPolicy(context, provider, tool)).isPresent();

    reloaded.revokeToolPolicy(requestId);

    ZalavaToolApprovalRequests reloadedAfterRevocation =
        new ZalavaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    assertThat(reloadedAfterRevocation.findAllowedToolPolicy(context, provider, tool)).isEmpty();
    assertThat(reloadedAfterRevocation.activeToolPolicies()).isEmpty();
  }

  @Test
  void expiredAndMalformedDurablePolicyExpiryFailsClosedAfterReload() {
    ZalavaProvider provider = provider("module-one");
    ZalavaToolDescriptor tool = new ZalavaToolDescriptor("write", "Writes data", true);
    InvocationContext expired =
        new InvocationContext(
            "actor-one",
            false,
            Map.of(
                ZalavaToolApprovalRequests.POLICY_CHANNEL_ID,
                "telegram",
                ZalavaToolApprovalRequests.POLICY_EXPIRES_AT,
                Instant.now().minusSeconds(1).toString()));
    String expiredId =
        requests.create(provider, tool, expired, JSON.createObjectNode()).requestId();
    requests.allowUnscopedTool(expiredId);

    InvocationContext malformed =
        new InvocationContext(
            "actor-two",
            false,
            Map.of(
                ZalavaToolApprovalRequests.POLICY_CHANNEL_ID,
                "telegram",
                ZalavaToolApprovalRequests.POLICY_EXPIRES_AT,
                "not-an-instant"));
    String malformedId =
        requests.create(provider, tool, malformed, JSON.createObjectNode()).requestId();
    requests.allowUnscopedTool(malformedId);

    ZalavaToolApprovalRequests reloaded =
        new ZalavaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));

    assertThat(reloaded.findAllowedToolPolicy(expired, provider, tool)).isEmpty();
    assertThat(reloaded.findAllowedToolPolicy(malformed, provider, tool)).isEmpty();
    assertThat(reloaded.activeToolPolicies()).isEmpty();
  }

  @Test
  void selectsTheNarrowestMatchingPolicyDeterministically() {
    ZalavaProvider provider = provider("module-one", Map.of("root", "workspace", "tenant", "home"));
    ZalavaToolDescriptor tool = new ZalavaToolDescriptor("write", "Writes data", true);
    InvocationContext context =
        new InvocationContext(
            "actor-one", false, Map.of(ZalavaToolApprovalRequests.POLICY_CHANNEL_ID, "telegram"));
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

    ZalavaToolApprovalRequests reloaded = new ZalavaToolApprovalRequests(store);

    assertThat(reloaded.findAllowedToolPolicy(context, provider, tool))
        .map(ZalavaToolApprovalRequests.Entry::requestId)
        .contains("00000000-0000-0000-0000-000000000002");
  }

  @Test
  void narrowsAnActivePolicyWithoutAllowingScopeOrExpiryWidening() {
    ZalavaProvider provider = provider("module-one", Map.of("root", "workspace", "tenant", "home"));
    ZalavaToolDescriptor tool = new ZalavaToolDescriptor("write", "Writes data", true);
    InvocationContext context =
        new InvocationContext(
            "actor-one", false, Map.of(ZalavaToolApprovalRequests.POLICY_CHANNEL_ID, "telegram"));
    FileSystemApprovalRequestStore store = new FileSystemApprovalRequestStore(workspace);
    String requestId = "00000000-0000-0000-0000-000000000003";
    store.save(policyEntry(requestId, provider, tool, context, Map.of()));
    ZalavaToolApprovalRequests managed = new ZalavaToolApprovalRequests(store);
    Instant expiry = Instant.now().plusSeconds(60);

    ZalavaToolApprovalRequests.Entry narrowed =
        managed.narrowToolPolicy(requestId, Map.of("root", "workspace"), expiry);

    assertThat(narrowed.scope()).containsExactly(Map.entry("root", "workspace"));
    assertThat(narrowed.attributes())
        .containsEntry(ZalavaToolApprovalRequests.POLICY_EXPIRES_AT, expiry.toString());
    assertThat(managed.findAllowedToolPolicy(context, provider, tool)).isPresent();
    assertThatThrownBy(() -> managed.narrowToolPolicy(requestId, Map.of(), null))
        .isInstanceOf(ZalavaToolApprovalRequests.PolicyNarrowingException.class);
    assertThatThrownBy(
            () ->
                managed.narrowToolPolicy(
                    requestId, Map.of("root", "workspace"), expiry.plusSeconds(1)))
        .isInstanceOf(ZalavaToolApprovalRequests.PolicyNarrowingException.class);
  }

  @Test
  void clearRemovesAllEntries() {
    addPending("p", "t", "a");
    var fresh = new ZalavaToolApprovalRequests(new FileSystemApprovalRequestStore(workspace));
    fresh.clear();
    assertThat(fresh.recentEntries()).isEmpty();
  }

  private static ZalavaToolApprovalRequests.Entry policyEntry(
      String requestId,
      ZalavaProvider provider,
      ZalavaToolDescriptor tool,
      InvocationContext context,
      Map<String, String> scope) {
    return new ZalavaToolApprovalRequests.Entry(
        requestId,
        "2026-01-01T00:00:00Z",
        "2026-01-01T00:00:00Z",
        null,
        null,
        provider.descriptor().providerId(),
        tool.name(),
        context.actorId(),
        Map.of(
            ZalavaToolApprovalRequests.POLICY_CHANNEL_ID,
            "telegram",
            ZalavaToolApprovalRequests.POLICY_MODULE_ID,
            provider.descriptor().moduleId()),
        scope,
        List.of(),
        true,
        JSON.createObjectNode(),
        "{}",
        null,
        ZalavaToolApprovalRequests.Decision.ALLOWED,
        ZalavaToolApprovalRequests.ApprovalScope.TOOL);
  }

  private static ZalavaProvider provider(String moduleId) {
    return provider(moduleId, Map.of());
  }

  private static ZalavaProvider provider(String moduleId, Map<String, String> scope) {
    return new ZalavaProvider() {
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
      public List<ZalavaToolDescriptor> listTools() {
        return List.of();
      }
    };
  }
}
