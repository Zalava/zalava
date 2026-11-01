package org.zalava.web.ui;

import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation.Choice;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.ToolKey;
import org.zalava.identity.accounts.application.UnifiedPermissions;
import org.zalava.identity.accounts.security.AuthenticatedActorResolver;

@Controller
public class UnifiedPermissionsController {
  private final UnifiedPermissions permissions;
  private final AuthenticatedActorResolver actors;

  public UnifiedPermissionsController(
      UnifiedPermissions permissions, AuthenticatedActorResolver actors) {
    this.permissions = permissions;
    this.actors = actors;
  }

  @GetMapping("/zalava/accounts/permissions")
  String page(
      @RequestParam(defaultValue = "member") String subject,
      Authentication auth,
      Model model,
      CsrfToken csrf) {
    var snapshot = permissions.snapshot(admin(auth), subject);
    model.addAttribute("people", snapshot.people());
    model.addAttribute("subject", subject);
    model.addAttribute("personSelected", snapshot.personSelected());
    model.addAttribute("memberSubject", snapshot.memberSubject());
    model.addAttribute("accountEnabled", snapshot.accountEnabled());
    model.addAttribute("toolRows", snapshot.toolRows());
    model.addAttribute("appRows", snapshot.appRows());
    model.addAttribute("csrf", csrf);
    return "accounts/permissions";
  }

  @PostMapping("/zalava/accounts/permissions/tool-access")
  String toolAccess(
      @RequestParam String subject,
      @RequestParam String moduleId,
      @RequestParam String providerId,
      @RequestParam String toolName,
      @RequestParam String decision,
      Authentication auth,
      RedirectAttributes redirect) {
    try {
      permissions.setToolAccess(
          admin(auth), subject, new ToolKey(moduleId, providerId, toolName), decision);
      redirect.addFlashAttribute("message", "Tool access saved.");
    } catch (IllegalArgumentException e) {
      redirect.addFlashAttribute(
          "error", "Tool access could not be saved. Check availability and restrictions.");
    }
    return back(subject, redirect);
  }

  @PostMapping("/zalava/accounts/permissions/confirmation")
  String confirm(
      @RequestParam String subject,
      @RequestParam String moduleId,
      @RequestParam String providerId,
      @RequestParam String toolName,
      @RequestParam String decision,
      Authentication auth,
      RedirectAttributes redirect) {
    try {
      permissions.setConfirmation(
          admin(auth),
          subject,
          new ToolKey(moduleId, providerId, toolName),
          Choice.valueOf(decision));
      redirect.addFlashAttribute("message", "Chat confirmation saved.");
    } catch (IllegalArgumentException e) {
      redirect.addFlashAttribute(
          "error", "Confirmation could not be saved. Check whether the tool is available.");
    }
    return back(subject, redirect);
  }

  @PostMapping("/zalava/accounts/permissions/app-access")
  String appAccess(
      @RequestParam String subject,
      @RequestParam String moduleId,
      @RequestParam String pageId,
      @RequestParam String decision,
      Authentication auth,
      RedirectAttributes redirect) {
    try {
      permissions.setAppAccess(admin(auth), subject, moduleId, pageId, decision);
      redirect.addFlashAttribute("message", "App access saved.");
    } catch (IllegalArgumentException e) {
      redirect.addFlashAttribute(
          "error", "App access could not be saved. Check whether the app is available.");
    }
    return back(subject, redirect);
  }

  @ExceptionHandler(NoSuchElementException.class)
  void unknownSubject() {
    throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  }

  private String admin(Authentication auth) {
    return actors.actor(auth).accountId().toString();
  }

  private static String back(String subject, RedirectAttributes redirect) {
    redirect.addAttribute("subject", subject);
    return "redirect:/zalava/accounts/permissions?subject={subject}";
  }
}
