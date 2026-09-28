package org.zalava.onboarding.api;

import jakarta.servlet.http.HttpSession;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.zalava.SupportedProvider;
import org.zalava.onboarding.application.OnboardingWorkflow;
import org.zalava.onboarding.domain.OnboardingPage;
import org.zalava.onboarding.domain.OnboardingSubmission;
import org.zalava.onboarding.steps.S5StarterModulesStep;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OnboardingController {

  private static final String COMPLETE_STEP_ID = "complete";
  private static final String ONBOARDING_TEMPLATE = "onboarding/index";

  private final OnboardingWorkflow workflow;

  public OnboardingController(OnboardingWorkflow workflow) {
    this.workflow = workflow;
  }

  @GetMapping({"/onboarding", "/onboarding/"})
  public String onboarding() {
    return "redirect:/onboarding/" + workflow.firstStepId();
  }

  @GetMapping("/onboarding/{stepId}")
  public String getStep(
      @PathVariable String stepId, HttpSession session, Model model, CsrfToken csrf) {
    // When arriving at the complete step via GET (e.g. by skipping the last optional step),
    // save configuration if the session still holds onboarding data.
    if (COMPLETE_STEP_ID.equals(stepId) && session.getAttribute("onboarding.provider") != null) {
      String providerLabel = saveAndComplete(session);
      if (providerLabel != null) model.addAttribute("providerLabel", providerLabel);
    }
    OnboardingPage page = workflow.page(stepId, sessionToMap(session), model.asMap());
    if (page == null) return "redirect:/onboarding/" + workflow.firstStepId();
    page.model().forEach(model::addAttribute);
    model.addAttribute("currentStep", page.title());
    model.addAttribute("currentStepNumber", page.stepNumber());
    model.addAttribute("totalSteps", page.totalSteps());
    model.addAttribute(
        "steps",
        page.steps().stream()
            .map(step -> Map.of("title", step.title(), "optional", step.optional()))
            .toList());
    model.addAttribute(
        "previousStepUrl",
        page.previousStepId() == null ? null : "/onboarding/" + page.previousStepId());
    model.addAttribute(
        "nextStepUrl", page.nextStepId() == null ? null : "/onboarding/" + page.nextStepId());
    model.addAttribute("isOptional", page.optional());
    model.addAttribute("stepTemplate", page.templatePath());
    model.addAttribute("csrf", csrf);
    return ONBOARDING_TEMPLATE;
  }

  @PostMapping("/onboarding/{stepId}")
  public String postStep(
      @PathVariable String stepId,
      @RequestParam Map<String, String> formParams,
      HttpSession session,
      RedirectAttributes redirectAttrs) {
    Map<String, Object> sessionMap = sessionToMap(session);
    OnboardingSubmission submission = workflow.submit(stepId, formParams, sessionMap);
    syncSessionFromMap(session, sessionMap);

    if (!submission.isAccepted()) {
      redirectAttrs.addFlashAttribute("error", submission.error());
      return "redirect:/onboarding/" + stepId;
    }

    if (S5StarterModulesStep.ID.equals(stepId)
        && S5StarterModulesStep.isSelectionUpdate(submission)) {
      return "redirect:/onboarding/" + stepId;
    }

    String nextId = submission.nextStepId();
    if (submission.completesOnboarding()) {
      String providerLabel = saveAndComplete(session);
      if (providerLabel != null) redirectAttrs.addFlashAttribute("providerLabel", providerLabel);
    }

    return "redirect:/onboarding/" + (nextId != null ? nextId : COMPLETE_STEP_ID);
  }

  private String saveAndComplete(HttpSession session) {
    Map<String, Object> finalSession = sessionToMap(session);
    String providerId = (String) finalSession.getOrDefault("onboarding.provider", "");
    String providerLabel =
        SupportedProvider.from(providerId).map(SupportedProvider::label).orElse(null);
    try {
      workflow.save(finalSession);
    } catch (Exception e) {
      throw new RuntimeException("Failed to save onboarding configuration", e);
    }
    clearOnboardingSession(session);
    return providerLabel;
  }

  private Map<String, Object> sessionToMap(HttpSession session) {
    Map<String, Object> map = new HashMap<>();
    Collections.list(session.getAttributeNames())
        .forEach(name -> map.put(name, session.getAttribute(name)));
    return map;
  }

  private void syncSessionFromMap(HttpSession session, Map<String, Object> map) {
    map.forEach(session::setAttribute);
  }

  private void clearOnboardingSession(HttpSession session) {
    Collections.list(session.getAttributeNames()).stream()
        .filter(name -> name.startsWith("onboarding."))
        .toList()
        .forEach(session::removeAttribute);
  }
}
