package org.zalava.memory.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.zalava.accounts.domain.AccountId;
import org.zalava.accounts.domain.Actor;
import org.zalava.files.YamlDocument;
import org.zalava.files.YamlParser;
import org.zalava.memory.domain.Memory;
import org.zalava.memory.domain.MemoryDraft;
import org.zalava.memory.domain.MemoryProvenance;
import org.zalava.memory.domain.MemoryScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * MEM-01 ownership, visibility, provenance, retention/deletion and backward-compatibility evidence
 * for the actor-scoped filesystem memory adapter.
 */
class FileSystemMemoryStoreOwnershipTest {

  @TempDir Path workspace;

  private final Actor actor = new Actor(AccountId.newId());
  private final Actor other = new Actor(AccountId.newId());

  @Test
  void scopeFiltersReturnOnlyTheRequestedMemoryIdentities() {
    var store = new FileSystemMemoryStore(workspace);
    store.remember(actor, new MemoryDraft(MemoryScope.USER, "user preference", Map.of()));
    store.remember(actor, new MemoryDraft(MemoryScope.PROJECT, "project decision", Map.of()));
    store.remember(
        actor,
        new MemoryDraft(
            MemoryScope.EXECUTION, "run trace", Map.of(), MemoryProvenance.of("task", "run-1")));

    assertThat(store.recent(actor, MemoryScope.durableScopes(), 10))
        .extracting(Memory::text)
        .containsExactlyInAnyOrder("user preference", "project decision");
    assertThat(store.recent(actor, Set.of(MemoryScope.EXECUTION), 10))
        .singleElement()
        .satisfies(memory -> assertThat(memory.text()).isEqualTo("run trace"));
    assertThat(store.search(actor, MemoryScope.durableScopes(), "decision", 10))
        .singleElement()
        .satisfies(memory -> assertThat(memory.text()).isEqualTo("project decision"));
  }

  @Test
  void emptyScopeFilterReturnsNothingAndRequiresNonNullScopes() {
    var store = new FileSystemMemoryStore(workspace);
    store.remember(actor, new MemoryDraft(MemoryScope.PROJECT, "anything", Map.of()));

    assertThat(store.recent(actor, Set.of(), 10)).isEmpty();
    assertThat(store.search(actor, Set.of(), "anything", 10)).isEmpty();
    assertThatThrownBy(() -> store.recent(actor, null, 10))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void provenanceRoundTripsAcrossReloadAndSearchesBySource() {
    var store = new FileSystemMemoryStore(workspace);
    Memory saved =
        store.remember(
            actor,
            new MemoryDraft(
                MemoryScope.AGENT,
                "prefer focused tests",
                Map.of("topic", "workflow"),
                MemoryProvenance.of("task", "run-7")));

    Memory reloaded = new FileSystemMemoryStore(workspace).find(actor, saved.id()).orElseThrow();

    assertThat(reloaded.provenance()).isEqualTo(MemoryProvenance.of("task", "run-7"));
    assertThat(reloaded.scope()).isEqualTo(MemoryScope.AGENT);
    assertThat(store.search(actor, MemoryScope.all(), "task", 10))
        .extracting(Memory::id)
        .containsExactly(saved.id());
  }

  @Test
  void actorRecordsAreNeverVisibleToAnotherActorThroughScopedQueries() {
    var store = new FileSystemMemoryStore(workspace);
    Memory saved = store.remember(actor, new MemoryDraft(MemoryScope.PROJECT, "private", Map.of()));

    assertThat(store.recent(other, MemoryScope.all(), 10)).isEmpty();
    assertThat(store.search(other, MemoryScope.all(), "private", 10)).isEmpty();
    assertThat(store.find(other, saved.id())).isEmpty();
  }

  @Test
  void findReturnsOnlyTheOwnedIdentity() {
    var store = new FileSystemMemoryStore(workspace);
    Memory saved = store.remember(actor, new MemoryDraft(MemoryScope.USER, "owned", Map.of()));

    assertThat(store.find(actor, saved.id())).get().extracting(Memory::text).isEqualTo("owned");
    assertThat(store.find(actor, "missing")).isEmpty();
  }

  @Test
  void deleteRemovesOneOwnedMemoryAndRefusesAnotherActorsRecord() {
    var store = new FileSystemMemoryStore(workspace);
    Memory first = store.remember(actor, new MemoryDraft(MemoryScope.PROJECT, "first", Map.of()));
    Memory second =
        store.remember(actor, new MemoryDraft(MemoryScope.EXECUTION, "second", Map.of()));

    assertThat(store.delete(other, first.id())).isFalse();
    assertThat(store.find(actor, first.id())).isPresent();
    assertThat(store.delete(actor, first.id())).isTrue();
    assertThat(store.find(actor, first.id())).isEmpty();
    assertThat(store.delete(actor, "missing")).isFalse();
    assertThat(store.find(actor, second.id())).isPresent();
  }

  @Test
  void deleteByScopeRetainsTheOtherDurableRecords() {
    var store = new FileSystemMemoryStore(workspace);
    store.remember(actor, new MemoryDraft(MemoryScope.USER, "keep", Map.of()));
    store.remember(
        actor,
        new MemoryDraft(
            MemoryScope.EXECUTION, "drop-1", Map.of(), MemoryProvenance.of("task", "run-1")));
    store.remember(
        actor,
        new MemoryDraft(
            MemoryScope.EXECUTION, "drop-2", Map.of(), MemoryProvenance.of("task", "run-2")));

    assertThat(store.delete(actor, Set.of(MemoryScope.EXECUTION))).isEqualTo(2);
    assertThat(store.recent(actor, MemoryScope.all(), 10))
        .singleElement()
        .satisfies(memory -> assertThat(memory.text()).isEqualTo("keep"));
  }

  @Test
  void recordsWrittenBeforeProvenanceLoadAsLegacy() throws Exception {
    Path directory =
        workspace.resolve("users").resolve(actor.accountId().toString()).resolve("memory");
    Files.createDirectories(directory);
    Map<String, String> frontmatter = new LinkedHashMap<>();
    frontmatter.put("id", "legacy-1");
    frontmatter.put("scope", "PROJECT");
    frontmatter.put("createdAt", Instant.parse("2026-01-01T00:00:00Z").toString());
    frontmatter.put("text", base64("old durable note"));
    Files.writeString(
        directory.resolve("legacy.yaml"),
        YamlParser.serialize(new YamlDocument(frontmatter, null)));

    var store = new FileSystemMemoryStore(workspace);

    assertThat(store.recent(actor, MemoryScope.all(), 10))
        .singleElement()
        .satisfies(
            memory -> {
              assertThat(memory.id()).isEqualTo("legacy-1");
              assertThat(memory.scope()).isEqualTo(MemoryScope.PROJECT);
              assertThat(memory.text()).isEqualTo("old durable note");
              assertThat(memory.provenance()).isEqualTo(MemoryProvenance.legacy());
            });
  }

  @Test
  void recordsWithAnInvalidScopeAreSkippedInsteadOfFailingTheListing() throws Exception {
    Path directory =
        workspace.resolve("users").resolve(actor.accountId().toString()).resolve("memory");
    Files.createDirectories(directory);
    Files.writeString(
        directory.resolve("invalid.yaml"),
        YamlParser.serialize(
            new YamlDocument(
                Map.of(
                    "id",
                    "bad-1",
                    "scope",
                    "global",
                    "createdAt",
                    Instant.EPOCH.toString(),
                    "text",
                    base64("unknown scope")),
                null)));

    var store = new FileSystemMemoryStore(workspace);

    assertThat(store.recent(actor, MemoryScope.all(), 10)).isEmpty();
    assertThat(store.find(actor, "bad-1")).isEmpty();
    assertThat(store.delete(actor, "bad-1")).isFalse();
  }

  private static String base64(String value) {
    return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
  }
}
