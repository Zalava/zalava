package org.zalava.web.ui;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.zalava.capabilities.operation.application.port.in.ChatToolConfirmation.Choice;
import org.zalava.capabilities.operation.application.port.in.MemberToolAccess.ToolKey;
import org.zalava.identity.accounts.application.UnifiedPermissions;
import org.zalava.identity.accounts.application.UnifiedPermissions.AppAccessChange;
import org.zalava.identity.accounts.application.UnifiedPermissions.ConfirmationChange;
import org.zalava.identity.accounts.application.UnifiedPermissions.ToolAccessChange;
import org.zalava.identity.accounts.domain.AccessDecision;
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
    if (!model.containsAttribute("submitted")) model.addAttribute("submitted", Map.of());
    model.addAttribute("people", snapshot.people());
    model.addAttribute("subject", subject);
    model.addAttribute("personSelected", snapshot.personSelected());
    model.addAttribute("memberSubject", snapshot.memberSubject());
    model.addAttribute("accountEnabled", snapshot.accountEnabled());
    model.addAttribute("groups", snapshot.groups());
    model.addAttribute("toolRows", snapshot.toolRows());
    model.addAttribute("appRows", snapshot.appRows());
    model.addAttribute("csrf", csrf);
    return "accounts/permissions";
  }

  @GetMapping("/zalava/accounts/permissions/view")
  String view(@RequestParam String subject, Authentication auth, Model model) {
    var snapshot = permissions.snapshot(admin(auth), subject);
    model.addAttribute("subject", subject);
    model.addAttribute("groups", snapshot.groups());
    model.addAttribute(
        "person",
        snapshot.people().stream()
            .filter(p -> p.id().equals(subject))
            .findFirst()
            .orElseThrow(NoSuchElementException::new));
    model.addAttribute("accountEnabled", snapshot.accountEnabled());
    return "accounts/permission-summary";
  }

  @PostMapping("/zalava/accounts/permissions/save")
  String save(
      @RequestParam String subject,
      @RequestParam MultiValueMap<String, String> fields,
      Authentication auth,
      RedirectAttributes redirect) {
    try {
      var changes = new ArrayList<UnifiedPermissions.Change>();
      for (var entry : fields.entrySet()) {
        String name = entry.getKey();
        if (!name.startsWith("p.")) continue;
        if (entry.getValue().size() != 1
            || fields.get("before." + name) == null
            || fields.get("before." + name).size() != 1)
          throw new IllegalArgumentException("Invalid form");
        String[] parts = name.split("\\.", -1);
        if (parts.length != 5) throw new IllegalArgumentException("Invalid form");
        String module = decode(parts[2]);
        String provider = decode(parts[3]);
        String target = decode(parts[4]);
        String before = fields.getFirst("before." + name);
        String decision = entry.getValue().getFirst();
        changes.add(
            switch (parts[1]) {
              case "access" ->
                  new ToolAccessChange(
                      new ToolKey(module, provider, target),
                      AccessDecision.valueOf(before),
                      AccessDecision.valueOf(decision));
              case "confirmation" ->
                  new ConfirmationChange(
                      new ToolKey(module, provider, target),
                      Choice.valueOf(before),
                      Choice.valueOf(decision));
              case "app" -> {
                if (!provider.equals("page"))
                  throw new IllegalArgumentException("Invalid app identity");
                yield new AppAccessChange(
                    module,
                    target,
                    AccessDecision.valueOf(before),
                    AccessDecision.valueOf(decision));
              }
              default -> throw new IllegalArgumentException("Unknown permission");
            });
      }
      permissions.saveBatch(admin(auth), subject, changes);
      redirect.addFlashAttribute("message", "Permissions saved.");
    } catch (IllegalArgumentException e) {
      var submitted = new LinkedHashMap<String, String>();
      fields.forEach(
          (key, values) -> {
            if (key.startsWith("p.") && values.size() == 1) submitted.put(key, values.getFirst());
          });
      redirect.addFlashAttribute("submitted", submitted);
      redirect.addFlashAttribute(
          "error", "Permissions could not be saved. Check your selections and try again.");
    }
    return back(subject, redirect);
  }

  private static String decode(String value) {
    return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
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
          admin(auth),
          subject,
          new ToolKey(moduleId, providerId, toolName),
          AccessDecision.valueOf(decision));
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
      permissions.setAppAccess(
          admin(auth), subject, moduleId, pageId, AccessDecision.valueOf(decision));
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
