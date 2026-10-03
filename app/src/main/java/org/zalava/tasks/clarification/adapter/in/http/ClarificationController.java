package org.zalava.tasks.clarification.adapter.in.http;

import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zalava.identity.accounts.security.AuthenticatedActorResolver;
import org.zalava.tasks.clarification.ClarificationText;
import org.zalava.tasks.clarification.ZalavaClarifications;
import org.zalava.tasks.clarification.application.port.in.ClarificationResponses;
import org.zalava.tasks.clarification.domain.ClarificationRequest;

/**
 * Actor-owned web surface for persisted clarifications. The authenticated principal is the only
 * source of ownership; request ids never authorize a response. It is separate from the approval
 * endpoints and never decides a tool policy.
 */
@RestController
@RequestMapping("/api/clarifications")
public final class ClarificationController {

  private final ClarificationResponses responses;
  private final AuthenticatedActorResolver actors;

  public ClarificationController(
      ClarificationResponses responses, AuthenticatedActorResolver actors) {
    this.responses = responses;
    this.actors = actors;
  }

  @GetMapping
  public List<ClarificationResponse> pending(Authentication authentication) {
    return responses.pending(actors.actor(authentication)).stream()
        .map(ClarificationResponse::from)
        .toList();
  }

  @GetMapping("/{requestId}")
  public ClarificationResponse get(@PathVariable String requestId, Authentication authentication) {
    return ClarificationResponse.from(responses.get(actors.actor(authentication), requestId));
  }

  /** Bounded safe-text rendering for a non-web channel; still owner-scoped. */
  @GetMapping(value = "/{requestId}/text", produces = MediaType.TEXT_PLAIN_VALUE)
  public String text(@PathVariable String requestId, Authentication authentication) {
    return ClarificationText.render(responses.get(actors.actor(authentication), requestId));
  }

  @PostMapping("/{requestId}/answer")
  public ClarificationResponse answer(
      @PathVariable String requestId,
      @RequestBody(required = false) AnswerRequest request,
      Authentication authentication) {
    if (request == null || request.answers() == null || request.answers().isEmpty()) {
      throw new IllegalArgumentException("A clarification answer is required");
    }
    List<ClarificationRequest.Answer> answers =
        request.answers().stream()
            .map(
                value ->
                    new ClarificationRequest.Answer(
                        value.questionId(), value.choiceId(), value.freeText()))
            .toList();
    return ClarificationResponse.from(
        responses.answer(actors.actor(authentication), requestId, answers));
  }

  @PostMapping("/{requestId}/cancel")
  public ClarificationResponse cancel(
      @PathVariable String requestId, Authentication authentication) {
    return ClarificationResponse.from(responses.cancel(actors.actor(authentication), requestId));
  }

  @ExceptionHandler(ZalavaClarifications.NotFoundException.class)
  ResponseEntity<Map<String, String>> notFound(ZalavaClarifications.NotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(Map.of("error", exception.getMessage()));
  }

  @ExceptionHandler(ZalavaClarifications.AlreadyAnsweredException.class)
  ResponseEntity<Map<String, String>> alreadyAnswered(
      ZalavaClarifications.AlreadyAnsweredException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", exception.getMessage()));
  }

  @ExceptionHandler(ZalavaClarifications.StaleClarificationException.class)
  ResponseEntity<Map<String, String>> stale(
      ZalavaClarifications.StaleClarificationException exception) {
    HttpStatus status =
        exception.status() == ClarificationRequest.Status.EXPIRED
            ? HttpStatus.GONE
            : HttpStatus.CONFLICT;
    return ResponseEntity.status(status).body(Map.of("error", exception.getMessage()));
  }

  @ExceptionHandler(ClarificationRequest.InvalidAnswerException.class)
  ResponseEntity<Map<String, String>> invalid(
      ClarificationRequest.InvalidAnswerException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("error", exception.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<Map<String, String>> illegalArgument(IllegalArgumentException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("error", exception.getMessage()));
  }

  public record AnswerRequest(List<AnswerValue> answers) {}

  public record AnswerValue(String questionId, String choiceId, String freeText) {}

  public record ClarificationResponse(
      String requestId,
      String status,
      String prompt,
      String taskReference,
      List<QuestionResponse> questions,
      List<AnswerValue> answers,
      String createdAt,
      String expiresAt,
      String answeredAt,
      String resolvedAt) {

    static ClarificationResponse from(ClarificationRequest request) {
      return new ClarificationResponse(
          request.requestId(),
          request.status().name(),
          request.prompt(),
          request.taskReference(),
          request.questions().stream().map(QuestionResponse::from).toList(),
          request.answers().stream()
              .map(value -> new AnswerValue(value.questionId(), value.choiceId(), value.freeText()))
              .toList(),
          request.createdAt(),
          request.expiresAt(),
          request.answeredAt(),
          request.resolvedAt());
    }
  }

  public record QuestionResponse(
      String questionId, String prompt, List<ChoiceResponse> choices, boolean freeTextAllowed) {
    static QuestionResponse from(ClarificationRequest.Question question) {
      return new QuestionResponse(
          question.questionId(),
          question.prompt(),
          question.choices().stream().map(ChoiceResponse::from).toList(),
          question.freeTextAllowed());
    }
  }

  public record ChoiceResponse(String choiceId, String label) {
    static ChoiceResponse from(ClarificationRequest.Choice choice) {
      return new ChoiceResponse(choice.choiceId(), choice.label());
    }
  }
}
