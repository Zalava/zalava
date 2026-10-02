package org.zalava.capabilities.discovery.adapter.out.springai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.toolsearch.ToolReference;
import org.springframework.ai.tool.toolsearch.ToolSearchRequest;
import org.zalava.capabilities.discovery.application.port.in.ToolDiscovery;

class SeaToolIndexTest {

  private static final ToolDiscovery.ToolMatch READ =
      match(
          "workspace-files",
          "Workspace Files",
          "readText",
          "Reads text from the workspace.",
          false,
          List.of("filesystem", "read"),
          List.of("sea_backed", "filesystem"),
          Map.of("root", "workspace"));
  private static final ToolDiscovery.ToolMatch WRITE =
      match(
          "workspace-files",
          "Workspace Files",
          "writeText",
          "Writes text in the workspace.",
          true,
          List.of("filesystem", "write"),
          List.of("sea_backed", "filesystem", "writable"),
          Map.of("root", "workspace"));

  private final SeaToolIndex index = new SeaToolIndex();

  @Test
  void returnsOnlyRegisteredSeaCallbacksForTheRequestedSession() {
    index.indexTools(
        "session-a", List.of(reference(READ), reference(WRITE), unrelated("createTask")));
    index.indexTool("session-b", reference(WRITE));

    assertThat(index.search(request("session-a", "workspace text", 5)).toolReferences())
        .extracting(ToolReference::toolName)
        .containsExactlyInAnyOrder(callbackName(READ), callbackName(WRITE));
    assertThat(index.search(request("session-b", "workspace text", 5)).toolReferences())
        .extracting(ToolReference::toolName)
        .containsExactly(callbackName(WRITE));
  }

  @Test
  void searchesOnlyTheRegisteredSessionSetBeforeApplyingResultLimit() {
    index.indexTool("session-a", reference(READ));
    for (int i = 0; i < 25; i++) {
      index.indexTool(
          "other-" + i,
          reference(
              match(
                  "provider-" + i,
                  "Other Provider",
                  "readOther" + i,
                  "Reads unrelated text.",
                  false,
                  List.of("read"),
                  List.of("sea_backed"),
                  Map.of())));
    }

    assertThat(index.search(request("session-a", "read", 1)).toolReferences())
        .extracting(ToolReference::toolName)
        .containsExactly(callbackName(READ));
  }

  @Test
  void returnsCompactProviderScopedSummaries() {
    index.indexTool("session-a", reference(WRITE));

    assertThat(index.search(request("session-a", "write", 5)).toolReferences())
        .singleElement()
        .satisfies(
            reference ->
                assertThat(reference.summary())
                    .contains("Workspace Files")
                    .contains("Writes text in the workspace.")
                    .contains("sideEffecting=true")
                    .contains("providerPolicyTags=")
                    .contains("root=workspace")
                    .contains("filesystem", "write"));
  }

  @Test
  void clearsOnlyTheRequestedSession() {
    index.indexTool("session-a", reference(READ));
    index.indexTool("session-b", reference(WRITE));

    index.clearIndex("session-a");

    assertThat(index.search(request("session-a", "text", 5)).toolReferences()).isEmpty();
    assertThat(index.search(request("session-b", "text", 5)).toolReferences())
        .extracting(ToolReference::toolName)
        .containsExactly(callbackName(WRITE));
  }

  @Test
  void rejectsInvalidAndUnsupportedSearchRequests() {
    assertThatThrownBy(() -> index.search(request(" ", "read", 5)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Spring AI tool search sessionId must not be blank");
    assertThatThrownBy(() -> index.search(request("session-a", " ", 5)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Spring AI tool search query must not be blank");
    assertThatThrownBy(() -> index.search(request("session-a", "read", 0)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Spring AI tool search maxResults must be between 1 and 10");
    assertThatThrownBy(() -> index.search(request("session-a", "read", 11)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Spring AI tool search maxResults must be between 1 and 10");
    assertThatThrownBy(
            () -> index.search(new ToolSearchRequest("session-a", "read", 5, "filesystem")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Spring AI regex tool search does not support categoryFilter");
  }

  private static ToolReference reference(ToolDiscovery.ToolMatch match) {
    return SeaToolReferences.from(match);
  }

  private static ToolReference unrelated(String name) {
    return ToolReference.builder().toolName(name).summary("Unrelated bootstrap tool.").build();
  }

  private static String callbackName(ToolDiscovery.ToolMatch match) {
    return SeaToolCallbackNames.forTool(match.providerId(), match.toolName());
  }

  private static ToolSearchRequest request(String sessionId, String query, Integer maxResults) {
    return new ToolSearchRequest(sessionId, query, maxResults, null);
  }

  private static ToolDiscovery.ToolMatch match(
      String providerId,
      String providerDisplayName,
      String toolName,
      String description,
      boolean sideEffecting,
      List<String> policyTags,
      List<String> providerPolicyTags,
      Map<String, String> scope) {
    return new ToolDiscovery.ToolMatch(
        providerId,
        providerDisplayName,
        toolName,
        description,
        sideEffecting,
        policyTags,
        providerPolicyTags,
        scope);
  }
}
