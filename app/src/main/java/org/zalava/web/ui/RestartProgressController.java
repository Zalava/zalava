package org.zalava.web.ui;

import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.zalava.modules.runtime.application.port.in.ManagedZalavaRestart;

@Controller
public class RestartProgressController {
  private final String generation = UUID.randomUUID().toString();
  private final ApplicationAvailability availability;
  private final ManagedZalavaRestart restart;

  public RestartProgressController(
      ApplicationAvailability availability,
      @Qualifier("rawManagedZalavaRestart") ManagedZalavaRestart restart) {
    this.availability = availability;
    this.restart = restart;
  }

  public String generation() {
    return generation;
  }

  @GetMapping("/restart-progress")
  String progress(@RequestParam UUID previous, Model model) {
    model.addAttribute("previous", previous.toString());
    return "ui/restart-progress";
  }

  @GetMapping("/restart-status")
  ResponseEntity<Map<String, Boolean>> status(@RequestParam UUID previous) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            Map.of(
                "ready",
                !generation.equals(previous.toString())
                    && availability.getReadinessState() == ReadinessState.ACCEPTING_TRAFFIC,
                "failed",
                restart.status().phase() == ManagedZalavaRestart.Phase.FAILED));
  }
}
