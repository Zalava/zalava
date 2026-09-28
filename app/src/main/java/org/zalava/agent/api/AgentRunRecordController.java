package org.zalava.agent.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.zalava.agent.application.port.in.AgentRunQueries;
import org.zalava.agent.domain.AgentRun;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/agent/runs")
@Profile({"dev", "test"})
public class AgentRunRecordController {

  private static final int DEFAULT_LIMIT = 50;
  private static final int MAX_LIMIT = 200;

  private final AgentRunQueries runQueries;

  public AgentRunRecordController(AgentRunQueries runQueries) {
    this.runQueries = runQueries;
  }

  @GetMapping
  public List<AgentRunRecordResponse> recent(
      @RequestParam(defaultValue = "" + DEFAULT_LIMIT) int limit) {
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be between 1 and 200");
    }

    List<AgentRun> records = new ArrayList<>(runQueries.recent());
    Collections.reverse(records);
    return records.stream().limit(limit).map(AgentRunRecordResponse::from).toList();
  }

  public record AgentRunRecordResponse(
      String id,
      String conversationId,
      AgentRun.PromptType promptType,
      String promptPreview,
      int selectedToolCount,
      int contextSourceCount,
      int contextCharacterBudget,
      int contextCharactersUsed,
      List<AgentRun.ContextSourceMetric> contextSourceMetrics,
      Instant startedAt,
      Instant completedAt,
      long durationMillis,
      AgentRun.Status status,
      String resultPreview,
      String errorPreview) {

    static AgentRunRecordResponse from(AgentRun record) {
      return new AgentRunRecordResponse(
          record.id(),
          record.conversationId(),
          record.promptType(),
          record.promptPreview(),
          record.selectedToolCount(),
          record.contextSourceCount(),
          record.contextCharacterBudget(),
          record.contextCharactersUsed(),
          record.contextSourceMetrics(),
          record.startedAt(),
          record.completedAt(),
          record.durationMillis(),
          record.status(),
          record.resultPreview(),
          record.errorPreview());
    }
  }
}
