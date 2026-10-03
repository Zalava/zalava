package org.zalava.web.ui;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public final class ZalavaLogsController {
  private final ZalavaApplicationLogs logs;

  public ZalavaLogsController(ZalavaApplicationLogs logs) {
    this.logs = logs;
  }

  @GetMapping("/monitoring/logs")
  public String logs(Model model) {
    model.addAttribute("logs", logs.latest());
    return "ui/zalava-logs";
  }
}
