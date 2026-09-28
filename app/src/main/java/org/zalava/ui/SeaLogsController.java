package org.zalava.ui;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public final class SeaLogsController {
  private final SeaApplicationLogs logs;

  public SeaLogsController(SeaApplicationLogs logs) {
    this.logs = logs;
  }

  @GetMapping("/monitoring/logs")
  public String logs(Model model) {
    model.addAttribute("logs", logs.latest());
    return "ui/sea-logs";
  }
}
