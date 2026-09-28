package org.zalava.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.zalava.accounts.domain.AccountRole;
import org.zalava.discovery.adapter.out.springai.SeaToolCallbackNames;
import org.zalava.discovery.adapter.out.springai.SeaToolIndex;
import org.zalava.discovery.adapter.out.springai.SeaToolReferences;
import org.zalava.discovery.application.port.in.ToolDiscovery;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.toolsearch.ToolIndex;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.ai.tool.toolsearch.ToolSearchRequest;
import org.springframework.ai.tool.toolsearch.ToolSearchResponse;

class PolicyFilteredToolSearchTest {

  private final DynamicToolActivationPolicy policy = new DynamicToolActivationPolicy();

  @Test
  void selectsOnlyQueryRelevantCandidates() {
    PolicyFilteredToolSearch search = new PolicyFilteredToolSearch(new SeaToolIndex());
    List<ToolDiscovery.ToolMatch> candidates =
        List.of(
            match("workspace", "readNote", "Reads a note from the workspace.", false, List.of()),
            match("workspace", "writeNote", "Writes a note to the workspace.", true, List.of()),
            match("telemetry", "ping", "Reports telemetry heartbeat.", false, List.of()));

    List<ToolDiscovery.ToolMatch> selected =
        search.select("session-a", "reads", candidates, null, 5);

    assertThat(selected).extracting(ToolDiscovery.ToolMatch::toolName).containsExactly("readNote");
  }

  @Test
  void reAppliesPolicyAndDropsBlockedCandidates() {
    PolicyFilteredToolSearch search =
        new PolicyFilteredToolSearch(new SeaToolIndex(), policy, true);
    List<ToolDiscovery.ToolMatch> candidates =
        List.of(
            match("workspace", "readNote", "Reads a note.", false, List.of()),
            match("host", "runCommand", "Runs a shell command.", true, List.of("shell")));

    List<ToolDiscovery.ToolMatch> selected =
        search.select("session-a", "run read command", candidates, null, 5);

    assertThat(selected).extracting(ToolDiscovery.ToolMatch::toolName).containsExactly("readNote");
  }

  @Test
  void rejectsMemberCandidatesWithoutMemberSafeScope() {
    PolicyFilteredToolSearch search = new PolicyFilteredToolSearch(new SeaToolIndex());
    ToolDiscovery.ToolMatch memberReady =
        new ToolDiscovery.ToolMatch(
            "shared",
            "Shared",
            "readNote",
            "Reads a shared note.",
            false,
            List.of("sea_backed", "member-safe"),
            List.of("sea_backed"),
            Map.of("household", "shared"));
    ToolDiscovery.ToolMatch memberMissing =
        match("private", "readSecret", "Reads a private note.", false, List.of("sea_backed"));

    List<ToolDiscovery.ToolMatch> selected =
        search.select(
            "session-a", "read note", List.of(memberReady, memberMissing), AccountRole.MEMBER, 5);

    assertThat(selected).extracting(ToolDiscovery.ToolMatch::toolName).containsExactly("readNote");
  }

  @Test
  void boundsResultsByMaxResultsAndKeepsTheIndexLimit() {
    PolicyFilteredToolSearch search = new PolicyFilteredToolSearch(new SeaToolIndex());
    List<ToolDiscovery.ToolMatch> candidates =
        java.util.stream.IntStream.range(0, 12)
            .mapToObj(
                index -> match("provider", "read" + index, "Reads note " + index, false, List.of()))
            .toList();

    List<ToolDiscovery.ToolMatch> selected =
        search.select("session-a", "reads note", candidates, null, 5);

    assertThat(selected).hasSize(5);
  }

  @Test
  void returnsNothingWhenDisabled() {
    PolicyFilteredToolSearch search =
        new PolicyFilteredToolSearch(new SeaToolIndex(), policy, false);
    List<ToolDiscovery.ToolMatch> candidates =
        List.of(match("workspace", "readNote", "Reads a note.", false, List.of()));

    assertThat(search.enabled()).isFalse();
    assertThat(search.select("session-a", "read note", candidates, null, 5)).isEmpty();
    assertThat(
            new PolicyFilteredToolSearch(null, policy, true)
                .select("session-a", "read note", candidates, null, 5))
        .isEmpty();
  }

  @Test
  void returnsNothingForBlankQueryOrEmptyCandidates() {
    PolicyFilteredToolSearch search = new PolicyFilteredToolSearch(new SeaToolIndex());

    assertThat(
            search.select(
                "session-a", " ", List.of(match("p", "t", "d", false, List.of())), null, 5))
        .isEmpty();
    assertThat(search.select("session-a", "read", List.of(), null, 5)).isEmpty();
    assertThat(
            search.select(" ", "read", List.of(match("p", "t", "read", false, List.of())), null, 5))
        .isEmpty();
  }

  @Test
  void fallsBackToNoSelectionWhenTheIndexFails() {
    ToolIndex failing =
        new ToolIndex() {
          @Override
          public void indexTool(String sessionId, ToolReference toolReference) {}

          @Override
          public ToolSearchResponse search(ToolSearchRequest request) {
            throw new IllegalStateException("index unavailable");
          }

          @Override
          public void clearIndex(String sessionId) {}
        };
    PolicyFilteredToolSearch search = new PolicyFilteredToolSearch(failing, policy, true);
    List<ToolDiscovery.ToolMatch> candidates =
        List.of(match("workspace", "readNote", "Reads a note.", false, List.of()));

    assertThat(search.select("session-a", "read note", candidates, null, 5)).isEmpty();
  }

  @Test
  void clearsOnlyTheSearchedSessionFromTheSharedIndex() {
    SeaToolIndex index = new SeaToolIndex();
    PolicyFilteredToolSearch search = new PolicyFilteredToolSearch(index, policy, true);
    ToolDiscovery.ToolMatch other = match("other", "otherTool", "Unrelated.", false, List.of());
    index.indexTool("session-b", SeaToolReferences.from(other));

    search.select(
        "session-a",
        "read note",
        List.of(match("workspace", "readNote", "Reads a note.", false, List.of())),
        null,
        5);

    assertThat(index.search(request("session-b", "unrelated", 5)).toolReferences())
        .extracting(ToolReference::toolName)
        .containsExactly(SeaToolCallbackNames.forTool("other", "otherTool"));
  }

  private static ToolDiscovery.ToolMatch match(
      String providerId,
      String toolName,
      String description,
      boolean sideEffecting,
      List<String> tags) {
    List<String> combined =
        java.util.stream.Stream.concat(java.util.stream.Stream.of("sea_backed"), tags.stream())
            .distinct()
            .toList();
    return new ToolDiscovery.ToolMatch(
        providerId,
        providerId,
        toolName,
        description,
        sideEffecting,
        combined,
        List.of("sea_backed"),
        Map.of("root", "workspace"));
  }

  private static ToolSearchRequest request(String sessionId, String query, int maxResults) {
    return new ToolSearchRequest(sessionId, query, maxResults, null);
  }
}
